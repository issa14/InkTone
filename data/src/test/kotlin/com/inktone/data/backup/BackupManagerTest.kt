package com.inktone.data.backup

import com.inktone.core.testing.fake.FakeBookmarkRepository
import com.inktone.core.testing.fake.FakePronunciationRuleRepository
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeThemeRepository
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.service.FileStorageService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream

private class InMemoryFileStorageService : FileStorageService {
    private val files = mutableMapOf<String, String>()

    override suspend fun openInputStream(uri: String): InputStream? = files[uri]?.byteInputStream()
    override suspend fun computeSha256(uri: String): String? = null
    override suspend fun getFileSize(uri: String): Long? = files[uri]?.length?.toLong()
    override suspend fun getFileName(uri: String): String? = uri.substringAfterLast('/')
    override suspend fun persistReadPermission(uri: String) = Unit
    override suspend fun writeToUri(uri: String, sourceFile: File): Boolean {
        files[uri] = sourceFile.readText()
        return true
    }
}

/**
 * Tache 8.5 — verifie le correctif par rapport au legacy : un signet ou
 * une session referencant une Publication disparue est compte a part
 * (skippedOrphans), jamais un crash sur l'import entier.
 */
class BackupManagerTest {

    @Test
    fun export_puis_import_restaure_les_donnees_valides_et_compte_les_orphelins() = runTest {
        val fileStorageService = InMemoryFileStorageService()
        val bookmarkRepository = FakeBookmarkRepository()
        val pronunciationRuleRepository = FakePronunciationRuleRepository()
        val readingStateRepository = FakeReadingStateRepository()
        val readingSessionRepository = FakeReadingSessionRepository()
        val publicationRepository = FakePublicationRepository()

        publicationRepository.insert(
            Publication(
                id = "pub-1", title = "Existe", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 1, importDate = 0L,
            ),
        )

        val backupManager = BackupManager(
            fileStorageService, bookmarkRepository, pronunciationRuleRepository,
            readingStateRepository, readingSessionRepository, publicationRepository,
            FakeThemeRepository(),
        )

        // Sauvegarde manuelle contenant un signet valide et un signet
        // orphelin (Publication "pub-disparue" absente du repository).
        val payload = BackupPayload(
            appVersion = "1.0", createdAt = 0L,
            bookmarks = listOf(
                BookmarkBackup(
                    id = "b1", publicationId = "pub-1",
                    locator = LocatorBackup("ch1.xhtml", 0, null, 0), createdAt = 0L,
                ),
                BookmarkBackup(
                    id = "b2", publicationId = "pub-disparue",
                    locator = LocatorBackup("ch1.xhtml", 0, null, 0), createdAt = 0L,
                ),
            ),
            pronunciationRules = emptyList(),
            readingStates = emptyList(),
            readingSessions = emptyList(),
        )
        val json = kotlinx.serialization.json.Json.encodeToString(BackupPayload.serializer(), payload)
        val backupFile = File.createTempFile("test-backup", ".json")
        backupFile.writeText(json)
        fileStorageService.writeToUri("backup://export", backupFile)
        backupFile.delete()

        val result = backupManager.importFrom("backup://export")

        assertTrue(result is ImportBackupResult.Success)
        result as ImportBackupResult.Success
        assertEquals(1, result.restored)
        assertEquals(1, result.skippedOrphans)
    }

    @Test
    fun exportTo_puis_importFrom_restitue_les_donnees_aller_retour_complet() = runTest {
        // Contrat Lot 6 (B1/B2) : pas seulement l'appel, un vrai aller-retour
        // via exportTo() (pas une construction manuelle de BackupPayload).
        val fileStorageService = InMemoryFileStorageService()
        val bookmarkRepository = FakeBookmarkRepository()
        val pronunciationRuleRepository = FakePronunciationRuleRepository()
        val readingStateRepository = FakeReadingStateRepository()
        val readingSessionRepository = FakeReadingSessionRepository()
        val publicationRepository = FakePublicationRepository()

        publicationRepository.insert(
            Publication(
                id = "pub-1", title = "Existe", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 1, importDate = 0L,
            ),
        )
        bookmarkRepository.insert(
            com.inktone.domain.model.Bookmark(
                id = "b1", publicationId = "pub-1",
                locator = com.inktone.domain.valueobject.Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 0),
                createdAt = 0L,
            ),
        )

        val exportManager = BackupManager(
            fileStorageService, bookmarkRepository, pronunciationRuleRepository,
            readingStateRepository, readingSessionRepository, publicationRepository,
            FakeThemeRepository(),
        )

        val exported = exportManager.exportTo("backup://roundtrip", appVersion = "1.2.3")
        assertTrue(exported)

        // Importe dans des repositories fraîchement vides — prouve que les
        // données proviennent bien du fichier exporté, pas d'un état partagé.
        val importManager = BackupManager(
            fileStorageService, FakeBookmarkRepository(), FakePronunciationRuleRepository(),
            FakeReadingStateRepository(), FakeReadingSessionRepository(), publicationRepository,
            FakeThemeRepository(),
        )
        val result = importManager.importFrom("backup://roundtrip")

        assertTrue(result is ImportBackupResult.Success)
        result as ImportBackupResult.Success
        assertEquals(1, result.restored)
        assertEquals(0, result.skippedOrphans)
    }

    @Test
    fun import_d_un_fichier_corrompu_echoue_proprement() = runTest {
        val fileStorageService = InMemoryFileStorageService()
        val backupFile = File.createTempFile("corrupted", ".json")
        backupFile.writeText("{ pas du json valide")
        fileStorageService.writeToUri("backup://corrupted", backupFile)
        backupFile.delete()

        val backupManager = BackupManager(
            fileStorageService, FakeBookmarkRepository(), FakePronunciationRuleRepository(),
            FakeReadingStateRepository(), FakeReadingSessionRepository(), FakePublicationRepository(),
            FakeThemeRepository(),
        )

        val result = backupManager.importFrom("backup://corrupted")

        assertTrue(result is ImportBackupResult.Failed)
    }
}
