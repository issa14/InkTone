package com.inktone.data.backup

import com.inktone.core.testing.fake.FakeAnnotationRepository
import com.inktone.core.testing.fake.FakeBookmarkRepository
import com.inktone.core.testing.fake.FakePronunciationRuleRepository
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeThemeRepository
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.service.FileStorageService
import com.inktone.domain.valueobject.Locator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream

/** Stocke des octets bruts — un export chiffré n'est pas un texte UTF-8 valide. */
private class InMemoryFileStorageService : FileStorageService {
    private val files = mutableMapOf<String, ByteArray>()

    override suspend fun openInputStream(uri: String): InputStream? = files[uri]?.inputStream()
    override suspend fun computeSha256(uri: String): String? = null
    override suspend fun getFileSize(uri: String): Long? = files[uri]?.size?.toLong()
    override suspend fun getFileName(uri: String): String? = uri.substringAfterLast('/')
    override suspend fun persistReadPermission(uri: String) = Unit
    override suspend fun writeToUri(uri: String, sourceFile: File): Boolean {
        files[uri] = sourceFile.readBytes()
        return true
    }

    fun rawBytes(uri: String): ByteArray? = files[uri]
}

private fun backupManager(
    fileStorageService: FileStorageService,
    bookmarkRepository: com.inktone.domain.repository.BookmarkRepository = FakeBookmarkRepository(),
    pronunciationRuleRepository: com.inktone.domain.repository.PronunciationRuleRepository = FakePronunciationRuleRepository(),
    readingStateRepository: com.inktone.domain.repository.ReadingStateRepository = FakeReadingStateRepository(),
    readingSessionRepository: com.inktone.domain.repository.ReadingSessionRepository = FakeReadingSessionRepository(),
    publicationRepository: com.inktone.domain.repository.PublicationRepository = FakePublicationRepository(),
    themeRepository: com.inktone.domain.repository.ThemeRepository = FakeThemeRepository(),
    annotationRepository: com.inktone.domain.repository.AnnotationRepository = FakeAnnotationRepository(),
) = BackupManager(
    fileStorageService, bookmarkRepository, pronunciationRuleRepository,
    readingStateRepository, readingSessionRepository, publicationRepository,
    themeRepository, annotationRepository,
)

/**
 * Tache 8.5, complete au lot 11 (tache 11.3) — verifie le correctif par
 * rapport au legacy (skippedOrphans) et le chiffrement E2EE : aller-retour
 * chiffre avec annotations, mauvais mot de passe, compatibilite
 * ascendante avec un export en clair pre-lot-11, mot de passe absent du
 * fichier.
 */
class BackupManagerTest {

    @Test
    fun export_puis_import_restaure_les_donnees_valides_et_compte_les_orphelins() = runTest {
        val fileStorageService = InMemoryFileStorageService()
        val publicationRepository = FakePublicationRepository()

        publicationRepository.insert(
            Publication(
                id = "pub-1", title = "Existe", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 1, importDate = 0L,
            ),
        )

        val backupManager = backupManager(fileStorageService, publicationRepository = publicationRepository)

        // Sauvegarde manuelle contenant un signet valide et un signet
        // orphelin (Publication "pub-disparue" absente du repository),
        // ecrite et chiffree comme le ferait exportTo.
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
        val envelope = BackupCrypto.encrypt(json.encodeToByteArray(), "mot-de-passe")
        val backupFile = File.createTempFile("test-backup", ".rfbackup")
        backupFile.writeBytes(envelope)
        fileStorageService.writeToUri("backup://export", backupFile)
        backupFile.delete()

        val result = backupManager.importFrom("backup://export", "mot-de-passe")

        assertTrue(result is ImportBackupResult.Success)
        result as ImportBackupResult.Success
        assertEquals(1, result.restored)
        assertEquals(1, result.skippedOrphans)
    }

    @Test
    fun exportTo_puis_importFrom_restitue_les_donnees_aller_retour_complet_annotations_comprises() = runTest {
        // Contrat Lot 6 (B1/B2) puis Lot 11 (11.1/11.3) : pas seulement
        // l'appel, un vrai aller-retour via exportTo()/importFrom()
        // chiffres, annotations comprises (defaut hérité du lot 6, corrigé
        // au lot 11).
        val fileStorageService = InMemoryFileStorageService()
        val bookmarkRepository = FakeBookmarkRepository()
        val annotationRepository = FakeAnnotationRepository()
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
                locator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 0),
                createdAt = 0L,
            ),
        )
        annotationRepository.insert(
            Annotation(
                id = "a1", publicationId = "pub-1",
                startLocator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 0),
                endLocator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 10),
                color = AnnotationColor.YELLOW, content = "une note", excerpt = "un extrait",
                createdAt = 0L, updatedAt = 0L,
            ),
        )

        val exportManager = backupManager(
            fileStorageService, bookmarkRepository = bookmarkRepository,
            publicationRepository = publicationRepository, annotationRepository = annotationRepository,
        )

        val exported = exportManager.exportTo("backup://roundtrip", appVersion = "1.2.3", password = "secret-1234")
        assertTrue(exported)

        // Importe dans des repositories fraîchement vides — prouve que les
        // données proviennent bien du fichier exporté, pas d'un état partagé.
        val importManager = backupManager(fileStorageService, publicationRepository = publicationRepository)
        val result = importManager.importFrom("backup://roundtrip", "secret-1234")

        assertTrue(result is ImportBackupResult.Success)
        result as ImportBackupResult.Success
        assertEquals(2, result.restored) // 1 signet + 1 annotation
        assertEquals(0, result.skippedOrphans)
    }

    @Test
    fun import_avec_un_mauvais_mot_de_passe_echoue_sans_rien_ecraser() = runTest {
        val fileStorageService = InMemoryFileStorageService()
        val publicationRepository = FakePublicationRepository()
        val exportManager = backupManager(fileStorageService, publicationRepository = publicationRepository)
        exportManager.exportTo("backup://wrongpw", appVersion = "1.0", password = "bon-mot-de-passe")

        val bookmarkRepository = FakeBookmarkRepository()
        val importManager = backupManager(
            fileStorageService, bookmarkRepository = bookmarkRepository, publicationRepository = publicationRepository,
        )
        val result = importManager.importFrom("backup://wrongpw", "mauvais-mot-de-passe")

        assertTrue(result is ImportBackupResult.Failed)
        assertTrue(bookmarkRepository.observeAll().first().isEmpty())
    }

    @Test
    fun import_sans_mot_de_passe_d_un_fichier_chiffre_echoue_proprement() = runTest {
        val fileStorageService = InMemoryFileStorageService()
        val exportManager = backupManager(fileStorageService)
        exportManager.exportTo("backup://needspw", appVersion = "1.0", password = "un-mot-de-passe")

        val result = backupManager(fileStorageService).importFrom("backup://needspw", password = null)

        assertTrue(result is ImportBackupResult.Failed)
    }

    @Test
    fun import_d_un_export_anterieur_en_clair_sans_annotations_est_accepte() = runTest {
        // Compatibilite ascendante (tache 11.1) : un fichier exporte avant
        // ce lot est un JSON en clair, sans le marqueur binaire de
        // BackupCrypto, et sans champ "annotations" — decodé grâce à sa
        // valeur par défaut.
        val fileStorageService = InMemoryFileStorageService()
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insert(
            Publication(
                id = "pub-1", title = "Existe", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 1, importDate = 0L,
            ),
        )
        val legacyPayload = BackupPayload(
            appVersion = "0.9", createdAt = 0L,
            bookmarks = listOf(
                BookmarkBackup(
                    id = "b1", publicationId = "pub-1",
                    locator = LocatorBackup("ch1.xhtml", 0, null, 0), createdAt = 0L,
                ),
            ),
            pronunciationRules = emptyList(), readingStates = emptyList(), readingSessions = emptyList(),
        )
        // Serialise sans le champ "annotations" — simule un vrai fichier legacy.
        val legacyJson = """{"appVersion":"0.9","createdAt":0,"bookmarks":[{"id":"b1","publicationId":"pub-1",""" +
            """"locator":{"resourceHref":"ch1.xhtml","chapterIndex":0,"charOffset":0},"createdAt":0}],""" +
            """"pronunciationRules":[],"readingStates":[],"readingSessions":[]}"""
        val legacyFile = File.createTempFile("legacy-backup", ".json")
        legacyFile.writeText(legacyJson)
        fileStorageService.writeToUri("backup://legacy", legacyFile)
        legacyFile.delete()

        val result = backupManager(fileStorageService, publicationRepository = publicationRepository)
            .importFrom("backup://legacy", password = null)

        assertTrue(result is ImportBackupResult.Success)
        result as ImportBackupResult.Success
        assertEquals(1, result.restored)
        assertEquals(0, legacyPayload.annotations.size)
    }

    @Test
    fun import_d_un_fichier_corrompu_echoue_proprement() = runTest {
        val fileStorageService = InMemoryFileStorageService()
        val backupFile = File.createTempFile("corrupted", ".json")
        backupFile.writeText("{ pas du json valide")
        fileStorageService.writeToUri("backup://corrupted", backupFile)
        backupFile.delete()

        val result = backupManager(fileStorageService).importFrom("backup://corrupted", password = null)

        assertTrue(result is ImportBackupResult.Failed)
    }

    @Test
    fun le_mot_de_passe_n_apparait_jamais_en_clair_dans_le_fichier_exporte() = runTest {
        val fileStorageService = InMemoryFileStorageService()
        val password = "mot-de-passe-secret-a-ne-pas-retrouver"
        backupManager(fileStorageService).exportTo("backup://secret", appVersion = "1.0", password = password)

        val bytes = fileStorageService.rawBytes("backup://secret")!!
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(password))
    }
}
