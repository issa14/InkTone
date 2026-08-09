package com.inktone.data.sync

import com.inktone.core.testing.fake.FakeAnnotationRepository
import com.inktone.core.testing.fake.FakeBookmarkRepository
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
import com.inktone.data.repository.RemoteDeviceFleetRepository
import com.inktone.data.repository.RemoteSyncActivityLogRepository
import com.inktone.domain.model.DeviceIdentity
import com.inktone.domain.model.SyncActivityEventType
import com.inktone.domain.service.SyncOperation
import com.inktone.domain.service.SyncOperationResult
import com.inktone.domain.service.FileStorageService
import kotlinx.coroutines.test.runTest
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

private fun backupManager() = BackupManager(
    NoopFileStorageService(), FakeBookmarkRepository(), FakePronunciationRuleRepository(),
    FakeReadingStateRepository(), FakeReadingSessionRepository(), FakePublicationRepository(),
    FakeThemeRepository(), FakeAnnotationRepository(),
)

/** Lot 11, tâche 11.9 — un instantané par appareil, garde contre un second déclenchement concurrent, flotte/journal mis à jour selon le résultat. */
class SyncNowManagerTest {

    private fun manager(
        syncProvider: FakeSyncProvider = FakeSyncProvider(),
        syncOperationTracker: FakeSyncOperationTracker = FakeSyncOperationTracker(),
        syncAccountRepository: FakeSyncAccountRepository = FakeSyncAccountRepository(),
    ) = SyncNowManager(
        syncProvider = syncProvider,
        backupManager = backupManager(),
        syncOperationTracker = syncOperationTracker,
        syncAccountRepository = syncAccountRepository,
        syncFleetRepository = RemoteDeviceFleetRepository(syncProvider),
        syncActivityLogRepository = RemoteSyncActivityLogRepository(syncProvider),
        deviceIdentityRepository = FakeDeviceIdentityRepository(DeviceIdentity("device-a", "Téléphone A")),
    )

    @Test
    fun synchronizeNow_reussit_televerse_l_instantane_propre_a_l_appareil_et_journalise_un_succes() = runTest {
        val syncProvider = FakeSyncProvider()
        val fleetRepository = RemoteDeviceFleetRepository(syncProvider)
        val activityLogRepository = RemoteSyncActivityLogRepository(syncProvider)
        val manager = SyncNowManager(
            syncProvider, backupManager(), FakeSyncOperationTracker(), FakeSyncAccountRepository(),
            fleetRepository, activityLogRepository, FakeDeviceIdentityRepository(DeviceIdentity("device-a", "Téléphone A")),
        )

        val result = manager.synchronizeNow()

        assertTrue(result is SyncOperationResult.Success)
        assertTrue(syncProvider.download("snapshot-device-a.json") != null)
        assertEquals(1, fleetRepository.listDevices().size)
        assertEquals("device-a", fleetRepository.listDevices().first().deviceId)
        assertEquals(SyncActivityEventType.SUCCESS, activityLogRepository.listEvents().first().type)
    }

    @Test
    fun synchronizeNow_refuse_un_second_declenchement_pendant_un_transfert_en_cours() = runTest {
        val tracker = FakeSyncOperationTracker()
        tracker.begin(SyncOperation.SYNCING)
        val syncProvider = FakeSyncProvider()
        val manager = manager(syncProvider = syncProvider, syncOperationTracker = tracker)

        val result = manager.synchronizeNow()

        assertTrue(result is SyncOperationResult.Failed)
        assertTrue(syncProvider.list().isEmpty()) // rien n'a ete televerse
    }

    @Test
    fun synchronizeNow_en_echec_journalise_un_echec_reseau_et_marque_le_compte_en_echec() = runTest {
        val syncProvider = FakeSyncProvider(failNextUpload = true)
        val activityLogRepository = RemoteSyncActivityLogRepository(syncProvider)
        val accountRepository = FakeSyncAccountRepository()
        val manager = SyncNowManager(
            syncProvider, backupManager(), FakeSyncOperationTracker(), accountRepository,
            RemoteDeviceFleetRepository(syncProvider), activityLogRepository,
            FakeDeviceIdentityRepository(DeviceIdentity("device-a", "Téléphone A")),
        )

        val result = manager.synchronizeNow()

        assertTrue(result is SyncOperationResult.Failed)
        assertEquals(SyncActivityEventType.NETWORK_FAILURE, activityLogRepository.listEvents().first().type)
    }

    @Test
    fun synchronizeNow_libere_la_garde_meme_en_cas_d_echec() = runTest {
        val tracker = FakeSyncOperationTracker()
        val manager = manager(syncProvider = FakeSyncProvider(failNextUpload = true), syncOperationTracker = tracker)

        manager.synchronizeNow()

        assertEquals(SyncOperation.NONE, tracker.observe().value)
    }
}
