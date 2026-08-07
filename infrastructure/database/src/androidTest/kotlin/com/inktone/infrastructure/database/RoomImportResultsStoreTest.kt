package com.inktone.infrastructure.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.usecase.ImportResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tâche 5.4 (points 2, 4, 5) — comportement de persistance de
 * `RoomImportResultsStore` :
 * - le nom de fichier est conservé et lisible après la fin du worker ;
 * - les résultats survivent à la reconstruction du store (même base) ;
 * - `beginSession` purge les sessions précédentes ;
 * - un import entièrement réussi, une fois fermé (`clearSession`), ne
 *   laisse aucun résidu à consulter.
 */
@RunWith(AndroidJUnit4::class)
class RoomImportResultsStoreTest {

    private lateinit var db: InkToneDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), InkToneDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() { db.close() }

    private fun newStore() = RoomImportResultsStore(db.importResultDao())

    private fun success(fileName: String) = ImportResult.Success(
        Publication(
            id = "pub-$fileName", title = "Titre $fileName", format = PublicationFormat.EPUB,
            fileUri = "content://x/$fileName", fileHash = "hash-$fileName", fileSize = 10L,
            chapterCount = 1, importDate = 0L,
        ),
    )

    @Test
    fun recordResult_conserve_le_nom_le_type_le_message_et_la_publication_existante() = runTest {
        val store = newStore()

        store.recordResult("session-1", "a.epub", ImportResult.Corrupted("Fichier illisible"))
        store.recordResult("session-1", "b.epub", ImportResult.Duplicate("pub-99"))
        store.recordResult("session-1", "c.epub", ImportResult.DrmProtected("Protege"))
        store.recordResult("session-1", "d.epub", ImportResult.UnsupportedFormat("PDF"))
        store.recordResult("session-1", "e.epub", success("e.epub"))

        val results = store.getResults("session-1")
        assertEquals(5, results.size)

        val corrupted = results.first { it.fileName == "a.epub" }
        assertEquals("corrupted", corrupted.resultType)
        assertEquals("Fichier illisible", corrupted.message)

        val duplicate = results.first { it.fileName == "b.epub" }
        assertEquals("duplicate", duplicate.resultType)
        assertEquals("pub-99", duplicate.existingPublicationId)

        val drm = results.first { it.fileName == "c.epub" }
        assertEquals("drm_protected", drm.resultType)
        assertEquals("Protege", drm.message)

        val unsupported = results.first { it.fileName == "d.epub" }
        assertEquals("unsupported_format", unsupported.resultType)
        assertEquals("Format non pris en charge : PDF", unsupported.message)

        val success = results.first { it.fileName == "e.epub" }
        assertEquals("success", success.resultType)
    }

    @Test
    fun beginSession_purge_les_resultats_des_sessions_precedentes() = runTest {
        val store = newStore()
        store.recordResult("session-1", "a.epub", ImportResult.Corrupted("Echec"))
        store.recordResult("session-1", "b.epub", ImportResult.Duplicate("pub-1"))

        store.beginSession("session-2")

        // L'ancienne session ne laisse plus de trace
        assertEquals(0, store.getResults("session-1").size)
        // La nouvelle session est vide et prête à accueillir ses résultats
        assertTrue(store.getResults("session-2").isEmpty())
    }

    @Test
    fun beginSession_ne_efface_jamais_la_session_courante_meme_en_retard() = runTest {
        val store = newStore()
        // Le worker a déjà commencé à écrire les résultats de la
        // nouvelle session (race : beginSession appelé en parallèle,
        // après le démarrage de WorkManager).
        store.recordResult("session-3", "a.epub", ImportResult.Corrupted("Echec"))

        store.beginSession("session-3")

        // La session courante reste intacte — un deleteAll aveugle
        // l'aurait effacée.
        val results = store.getResults("session-3")
        assertEquals(1, results.size)
        assertEquals("a.epub", results.first().fileName)
    }

    @Test
    fun un_import_reussi_ferme_ne_laisse_aucun_residu_a_consulter() = runTest {
        val store = newStore()
        store.recordResult("session-1", "a.epub", success("a.epub"))
        store.recordResult("session-1", "b.epub", success("b.epub"))

        // Consultation (résumé affiché), puis fermeture (DismissImportResults)
        assertEquals(2, store.getResults("session-1").size)
        store.clearSession("session-1")

        assertTrue(store.getResults("session-1").isEmpty())
        assertEquals(0, db.importResultDao().getBySession("session-1").size)
    }

    @Test
    fun les_resultats_survivent_a_la_mort_du_processus() = runTest {
        // Base sur fichier (pas en mémoire) : fermer la connexion puis en
        // rouvrir une nouvelle sur le même fichier simule fidèlement un
        // kill de process, contrairement à une simple reconstruction du
        // store sur une base en mémoire qui ne prouve rien de tel.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "import-results-process-death-test.db"
        context.deleteDatabase(dbName)

        try {
            val firstProcessDb = Room.databaseBuilder(context, InkToneDatabase::class.java, dbName).build()
            RoomImportResultsStore(firstProcessDb.importResultDao())
                .recordResult("session-1", "a.epub", ImportResult.Corrupted("Echec"))
            firstProcessDb.close()

            // Le process est mort : nouvelle instance de base, nouveau store.
            val secondProcessDb = Room.databaseBuilder(context, InkToneDatabase::class.java, dbName).build()
            val results = RoomImportResultsStore(secondProcessDb.importResultDao()).getResults("session-1")
            secondProcessDb.close()

            assertEquals(1, results.size)
            assertEquals("a.epub", results.first().fileName)
            assertEquals("Echec", results.first().message)
        } finally {
            context.deleteDatabase(dbName)
        }
    }
}
