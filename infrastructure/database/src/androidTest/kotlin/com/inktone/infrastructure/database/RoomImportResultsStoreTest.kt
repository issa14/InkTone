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
    fun les_resultats_survivent_a_une_reconstruction_du_store() = runTest {
        val store = newStore()
        store.recordResult("session-1", "a.epub", ImportResult.Corrupted("Echec"))

        // Reconstruit un nouveau store sur la même base — simule la survie
        // à la mort du processus (la base est en mémoire ici, mais le
        // contrat de persistance Room est le même).
        val storeReloaded = newStore()
        val results = storeReloaded.getResults("session-1")

        assertEquals(1, results.size)
        assertEquals("a.epub", results.first().fileName)
        assertEquals("Echec", results.first().message)
    }
}
