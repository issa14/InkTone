package com.inktone.data.sync

import com.inktone.core.testing.fake.FakeAnnotationRepository
import com.inktone.core.testing.fake.FakeBookmarkRepository
import com.inktone.core.testing.fake.FakeConflictQueueRepository
import com.inktone.core.testing.fake.FakeDeviceIdentityRepository
import com.inktone.core.testing.fake.FakePronunciationRuleRepository
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeSyncAccountRepository
import com.inktone.core.testing.fake.FakeSyncOperationTracker
import com.inktone.core.testing.fake.FakeSyncProvider
import com.inktone.core.testing.fake.FakeThemeRepository
import com.inktone.data.backup.BackupManager
import com.inktone.data.backup.backupJson
import com.inktone.data.repository.RemoteDeviceFleetRepository
import com.inktone.data.repository.RemoteSyncActivityLogRepository
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.DeviceIdentity
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.SyncActivityEventType
import com.inktone.domain.repository.AnnotationRepository
import com.inktone.domain.repository.BookmarkRepository
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingSessionRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.service.FileStorageService
import com.inktone.domain.service.SyncOperation
import com.inktone.domain.service.SyncOperationResult
import com.inktone.domain.valueobject.Locator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream

private class NoopFileStorageService : FileStorageService {
    override suspend fun openInputStream(uri: String): InputStream? = null
    override suspend fun computeSha256(uri: String): String? = null
    override suspend fun getFileSize(uri: String): Long? = null
    override suspend fun getFileName(uri: String): String? = null
    override suspend fun persistReadPermission(uri: String) = Unit
    override suspend fun writeToUri(uri: String, sourceFile: File): Boolean = true
}

private fun backupManager(
    bookmarkRepository: BookmarkRepository = FakeBookmarkRepository(),
    annotationRepository: AnnotationRepository = FakeAnnotationRepository(),
    readingStateRepository: ReadingStateRepository = FakeReadingStateRepository(),
    readingSessionRepository: ReadingSessionRepository = FakeReadingSessionRepository(),
    publicationRepository: PublicationRepository = FakePublicationRepository(),
) = BackupManager(
    NoopFileStorageService(), bookmarkRepository, FakePronunciationRuleRepository(),
    readingStateRepository, readingSessionRepository, publicationRepository,
    FakeThemeRepository(), annotationRepository,
)

private fun publication(id: String, title: String = "Livre $id") = Publication(
    id = id, title = title, format = PublicationFormat.EPUB, fileUri = "content://x",
    fileHash = "hash", fileSize = 10, chapterCount = 20, importDate = 0L,
)

/** Lot 11, tâche 11.9/11.11 — un instantané par appareil, garde contre un second déclenchement concurrent, fusion silencieuse des données additives, détection de conflit de position. */
class SyncNowManagerTest {

    private class Fixture(
        val syncProvider: FakeSyncProvider = FakeSyncProvider(),
        val bookmarkRepository: FakeBookmarkRepository = FakeBookmarkRepository(),
        val annotationRepository: FakeAnnotationRepository = FakeAnnotationRepository(),
        val readingStateRepository: FakeReadingStateRepository = FakeReadingStateRepository(),
        val readingSessionRepository: FakeReadingSessionRepository = FakeReadingSessionRepository(),
        val publicationRepository: FakePublicationRepository = FakePublicationRepository(),
        val conflictQueueRepository: FakeConflictQueueRepository = FakeConflictQueueRepository(),
        val syncOperationTracker: FakeSyncOperationTracker = FakeSyncOperationTracker(),
        val syncAccountRepository: FakeSyncAccountRepository = FakeSyncAccountRepository(),
        deviceIdentity: DeviceIdentity = DeviceIdentity("device-a", "Téléphone A"),
    ) {
        val fleetRepository = RemoteDeviceFleetRepository(syncProvider)
        val activityLogRepository = RemoteSyncActivityLogRepository(syncProvider)
        val manager = SyncNowManager(
            syncProvider = syncProvider,
            backupManager = backupManager(bookmarkRepository, annotationRepository, readingStateRepository, readingSessionRepository, publicationRepository),
            syncOperationTracker = syncOperationTracker,
            syncAccountRepository = syncAccountRepository,
            syncFleetRepository = fleetRepository,
            syncActivityLogRepository = activityLogRepository,
            deviceIdentityRepository = FakeDeviceIdentityRepository(deviceIdentity),
            bookmarkRepository = bookmarkRepository,
            annotationRepository = annotationRepository,
            pronunciationRuleRepository = FakePronunciationRuleRepository(),
            themeRepository = FakeThemeRepository(),
            readingSessionRepository = readingSessionRepository,
            readingStateRepository = readingStateRepository,
            publicationRepository = publicationRepository,
            conflictQueueRepository = conflictQueueRepository,
        )
    }

    @Test
    fun synchronizeNow_reussit_televerse_l_instantane_propre_a_l_appareil_et_journalise_un_succes() = runTest {
        val fixture = Fixture()

        val result = fixture.manager.synchronizeNow()

        assertTrue(result is SyncOperationResult.Success)
        assertTrue(fixture.syncProvider.download("snapshot-device-a.json") != null)
        assertEquals(1, fixture.fleetRepository.listDevices().size)
        assertEquals("device-a", fixture.fleetRepository.listDevices().first().deviceId)
        assertEquals(SyncActivityEventType.SUCCESS, fixture.activityLogRepository.listEvents().first().type)
    }

    @Test
    fun synchronizeNow_refuse_un_second_declenchement_pendant_un_transfert_en_cours() = runTest {
        val tracker = FakeSyncOperationTracker()
        tracker.begin(SyncOperation.SYNCING)
        val fixture = Fixture(syncOperationTracker = tracker)

        val result = fixture.manager.synchronizeNow()

        assertTrue(result is SyncOperationResult.Failed)
        assertTrue(fixture.syncProvider.list().isEmpty()) // rien n'a ete televerse
    }

    @Test
    fun synchronizeNow_en_echec_journalise_un_echec_reseau_et_marque_le_compte_en_echec() = runTest {
        val fixture = Fixture(syncProvider = FakeSyncProvider(failNextUpload = true))

        val result = fixture.manager.synchronizeNow()

        assertTrue(result is SyncOperationResult.Failed)
        assertEquals(SyncActivityEventType.NETWORK_FAILURE, fixture.activityLogRepository.listEvents().first().type)
    }

    @Test
    fun synchronizeNow_libere_la_garde_meme_en_cas_d_echec() = runTest {
        val tracker = FakeSyncOperationTracker()
        val fixture = Fixture(syncProvider = FakeSyncProvider(failNextUpload = true), syncOperationTracker = tracker)

        fixture.manager.synchronizeNow()

        assertEquals(SyncOperation.NONE, tracker.observe().value)
    }

    @Test
    fun synchronizeNow_fusionne_silencieusement_les_annotations_d_un_autre_appareil() = runTest {
        val syncProvider = FakeSyncProvider()
        val publicationRepository = FakePublicationRepository().apply { insert(publication("pub-1")) }

        // Un autre appareil a deja televerse son instantane, avant que celui-ci ne synchronise.
        val remotePayload = com.inktone.data.backup.BackupPayload(
            appVersion = "sync", createdAt = 0L, bookmarks = emptyList(), pronunciationRules = emptyList(),
            readingStates = emptyList(), readingSessions = emptyList(),
            annotations = listOf(
                com.inktone.data.backup.AnnotationBackup(
                    id = "remote-annotation", publicationId = "pub-1",
                    startLocator = com.inktone.data.backup.LocatorBackup("ch1.xhtml", 0, null, 0),
                    endLocator = com.inktone.data.backup.LocatorBackup("ch1.xhtml", 0, null, 10),
                    color = "YELLOW", createdAt = 0L, updatedAt = 0L,
                ),
            ),
        )
        syncProvider.upload("snapshot-device-b.json", backupJson.encodeToString(remotePayload).encodeToByteArray())

        val fixture = Fixture(syncProvider = syncProvider, publicationRepository = publicationRepository)

        fixture.manager.synchronizeNow()

        assertTrue(fixture.annotationRepository.getAll().any { it.id == "remote-annotation" })
    }

    @Test
    fun synchronizeNow_n_ecrase_pas_une_annotation_locale_deja_presente_avec_le_meme_id() = runTest {
        val syncProvider = FakeSyncProvider()
        val publicationRepository = FakePublicationRepository().apply { insert(publication("pub-1")) }
        val annotationRepository = FakeAnnotationRepository().apply {
            insert(
                Annotation(
                    id = "shared-id", publicationId = "pub-1",
                    startLocator = Locator("ch1.xhtml", 0, null, 0), endLocator = Locator("ch1.xhtml", 0, null, 10),
                    color = AnnotationColor.GREEN, content = "locale", createdAt = 0L, updatedAt = 0L,
                ),
            )
        }
        val remotePayload = com.inktone.data.backup.BackupPayload(
            appVersion = "sync", createdAt = 0L, bookmarks = emptyList(), pronunciationRules = emptyList(),
            readingStates = emptyList(), readingSessions = emptyList(),
            annotations = listOf(
                com.inktone.data.backup.AnnotationBackup(
                    id = "shared-id", publicationId = "pub-1",
                    startLocator = com.inktone.data.backup.LocatorBackup("ch1.xhtml", 0, null, 0),
                    endLocator = com.inktone.data.backup.LocatorBackup("ch1.xhtml", 0, null, 10),
                    color = "YELLOW", content = "distante", createdAt = 0L, updatedAt = 0L,
                ),
            ),
        )
        syncProvider.upload("snapshot-device-b.json", backupJson.encodeToString(remotePayload).encodeToByteArray())

        val fixture = Fixture(syncProvider = syncProvider, publicationRepository = publicationRepository, annotationRepository = annotationRepository)

        fixture.manager.synchronizeNow()

        assertEquals(1, fixture.annotationRepository.getAll().size)
        assertEquals("locale", fixture.annotationRepository.getAll().first().content)
    }

    @Test
    fun synchronizeNow_adopte_la_position_distante_quand_il_n_y_a_pas_de_position_locale() = runTest {
        val syncProvider = FakeSyncProvider()
        val publicationRepository = FakePublicationRepository().apply { insert(publication("pub-1")) }
        val remotePayload = com.inktone.data.backup.BackupPayload(
            appVersion = "sync", createdAt = 0L, bookmarks = emptyList(), pronunciationRules = emptyList(),
            readingStates = listOf(
                com.inktone.data.backup.ReadingStateBackup(
                    publicationId = "pub-1", locator = com.inktone.data.backup.LocatorBackup("ch5.xhtml", 5, null, 0), lastReadAt = 100L,
                ),
            ),
            readingSessions = emptyList(), annotations = emptyList(),
        )
        syncProvider.upload("snapshot-device-b.json", backupJson.encodeToString(remotePayload).encodeToByteArray())

        val fixture = Fixture(syncProvider = syncProvider, publicationRepository = publicationRepository)

        fixture.manager.synchronizeNow()

        assertEquals(5, fixture.readingStateRepository.get("pub-1")?.locator?.chapterIndex)
        assertTrue(fixture.conflictQueueRepository.listPending().isEmpty())
    }

    @Test
    fun synchronizeNow_met_en_file_un_conflit_quand_les_positions_divergent() = runTest {
        val syncProvider = FakeSyncProvider()
        val publicationRepository = FakePublicationRepository().apply { insert(publication("pub-1", title = "Le Grand Livre")) }
        val readingStateRepository = FakeReadingStateRepository().apply {
            save(ReadingState("pub-1", Locator("ch1.xhtml", 1, null, 0), lastReadAt = 50L))
        }
        val remotePayload = com.inktone.data.backup.BackupPayload(
            appVersion = "sync", createdAt = 0L, bookmarks = emptyList(), pronunciationRules = emptyList(),
            readingStates = listOf(
                com.inktone.data.backup.ReadingStateBackup(
                    publicationId = "pub-1", locator = com.inktone.data.backup.LocatorBackup("ch8.xhtml", 8, null, 0), lastReadAt = 200L,
                ),
            ),
            readingSessions = emptyList(), annotations = emptyList(),
        )
        syncProvider.upload("snapshot-device-b.json", backupJson.encodeToString(remotePayload).encodeToByteArray())

        val fixture = Fixture(syncProvider = syncProvider, publicationRepository = publicationRepository, readingStateRepository = readingStateRepository)

        fixture.manager.synchronizeNow()

        // La position locale n'est jamais ecrasee automatiquement par la synchro.
        assertEquals(1, fixture.readingStateRepository.get("pub-1")?.locator?.chapterIndex)
        val conflicts = fixture.conflictQueueRepository.listPending()
        assertEquals(1, conflicts.size)
        assertEquals("Le Grand Livre", conflicts.first().bookTitle)
        assertEquals(1, conflicts.first().local.chapterIndex)
        assertEquals(8, conflicts.first().remote.chapterIndex)
    }

    @Test
    fun synchronizeNow_ne_met_pas_en_file_un_second_conflit_pour_la_meme_publication() = runTest {
        val syncProvider = FakeSyncProvider()
        val publicationRepository = FakePublicationRepository().apply { insert(publication("pub-1")) }
        val readingStateRepository = FakeReadingStateRepository().apply {
            save(ReadingState("pub-1", Locator("ch1.xhtml", 1, null, 0), lastReadAt = 50L))
        }
        val conflictQueueRepository = FakeConflictQueueRepository()
        val remotePayload = com.inktone.data.backup.BackupPayload(
            appVersion = "sync", createdAt = 0L, bookmarks = emptyList(), pronunciationRules = emptyList(),
            readingStates = listOf(
                com.inktone.data.backup.ReadingStateBackup(
                    publicationId = "pub-1", locator = com.inktone.data.backup.LocatorBackup("ch8.xhtml", 8, null, 0), lastReadAt = 200L,
                ),
            ),
            readingSessions = emptyList(), annotations = emptyList(),
        )
        syncProvider.upload("snapshot-device-b.json", backupJson.encodeToString(remotePayload).encodeToByteArray())
        val fixture = Fixture(
            syncProvider = syncProvider, publicationRepository = publicationRepository,
            readingStateRepository = readingStateRepository, conflictQueueRepository = conflictQueueRepository,
        )

        fixture.manager.synchronizeNow()
        fixture.manager.synchronizeNow()

        assertEquals(1, fixture.conflictQueueRepository.listPending().size)
    }

    @Test
    fun synchronizeNow_fusionne_les_sessions_de_lecture_distantes_par_identifiant() = runTest {
        val syncProvider = FakeSyncProvider()
        val publicationRepository = FakePublicationRepository().apply { insert(publication("pub-1")) }
        val readingSessionRepository = FakeReadingSessionRepository()
        val remotePayload = com.inktone.data.backup.BackupPayload(
            appVersion = "sync", createdAt = 0L, bookmarks = emptyList(), pronunciationRules = emptyList(),
            readingStates = emptyList(), annotations = emptyList(),
            readingSessions = listOf(
                com.inktone.data.backup.ReadingSessionBackup(
                    id = "session-1", publicationId = "pub-1", startedAt = 0L, endedAt = 100L,
                    mode = "VISUAL", sentencesRead = 5, wordsRead = 50, visualDurationMs = 10_000, ttsDurationMs = 0,
                ),
            ),
        )
        syncProvider.upload("snapshot-device-b.json", backupJson.encodeToString(remotePayload).encodeToByteArray())

        val fixture = Fixture(
            syncProvider = syncProvider, publicationRepository = publicationRepository,
            readingSessionRepository = readingSessionRepository,
        )

        fixture.manager.synchronizeNow()

        assertEquals(1, readingSessionRepository.getAll().size)
        assertEquals(50, readingSessionRepository.getAll().first().wordsRead)
        assertEquals(10_000, readingSessionRepository.getAll().first().visualDurationMs)
    }

    @Test
    fun synchronizeNow_ignore_et_journalise_les_sessions_dont_le_livre_est_absent() = runTest {
        val syncProvider = FakeSyncProvider()
        val readingSessionRepository = FakeReadingSessionRepository()
        val remotePayload = com.inktone.data.backup.BackupPayload(
            appVersion = "sync", createdAt = 0L, bookmarks = emptyList(), pronunciationRules = emptyList(),
            readingStates = emptyList(), annotations = emptyList(),
            readingSessions = listOf(
                com.inktone.data.backup.ReadingSessionBackup(
                    id = "orphan", publicationId = "pub-absent", startedAt = 0L, endedAt = 100L, mode = "VISUAL",
                ),
            ),
        )
        syncProvider.upload("snapshot-device-b.json", backupJson.encodeToString(remotePayload).encodeToByteArray())

        val fixture = Fixture(syncProvider = syncProvider, readingSessionRepository = readingSessionRepository)

        fixture.manager.synchronizeNow()

        // Jamais insérée (violerait la clé étrangère), mais journalisée.
        assertTrue(readingSessionRepository.getAll().isEmpty())
        assertTrue(fixture.activityLogRepository.listEvents().any { it.message.contains("ignorée") })
    }
}
