package com.inktone.infrastructure.database.search

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.infrastructure.database.InkToneDatabase
import com.inktone.infrastructure.database.entity.SentenceFtsEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Tâche 7.6 — pas supposer que FTS4 « est rapide par nature » sans le
 * mesurer sur notre volume réel, et vérifier l'échappement des caractères
 * spéciaux (source classique de bugs de recherche silencieux : une
 * requête qui ne plante pas mais ne retourne jamais rien).
 *
 * **Portée délibérément différente du corpus du benchmark d'import**
 * (Tâche 6.9, `ImportBenchmarkTest`, 500 EPUB) : cette tâche mesure la
 * *recherche* sur un index déjà peuplé, pas le *coût d'indexation à
 * l'import* (déjà couvert — `ImportBenchmarkTest` utilise maintenant
 * `RoomSearchService` réel, Tâche 7.3). Peupler l'index directement via
 * `SentenceFtsDao` (pas de vrai parsing EPUB) suffit pour ce qui est
 * mesuré ici et évite de dupliquer le générateur de corpus d'un autre
 * module.
 */
@RunWith(AndroidJUnit4::class)
class RoomSearchServiceTest {

    private lateinit var db: InkToneDatabase
    private lateinit var searchService: RoomSearchService

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), InkToneDatabase::class.java,
        ).build()
        searchService = RoomSearchService(db.sentenceFtsDao())

        // Corpus synthetique de volume - plusieurs publications, texte
        // varie pour ne pas biaiser FTS4 vers un cas trivial (une seule
        // phrase repetee).
        val entities = (0 until SENTENCE_COUNT).map { index ->
            SentenceFtsEntity(
                publicationId = "pub-${index % 20}",
                chapterIndex = index % 10,
                resourceHref = "chapter${index % 10}.xhtml",
                charOffset = index,
                text = "Phrase numero $index parlant de test et de recherche dans un roman imaginaire.",
            )
        }
        db.sentenceFtsDao().insertAll(entities)
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun recherche_reste_rapide_sur_corpus_volumineux() = runTest {
        val elapsed = measureTimeMillis { searchService.search("test") }
        assertTrue("recherche sous 200ms attendue, mesure: ${elapsed}ms", elapsed < 200)
    }

    @Test
    fun une_recherche_reelle_trouve_bien_les_phrases_indexees() = runTest {
        val results = searchService.search("imaginaire")
        assertTrue("attendu au moins un resultat sur un terme present dans tout le corpus", results.isNotEmpty())
    }

    @Test
    fun recherche_filtree_par_publication_ne_retourne_que_cette_publication() = runTest {
        val results = searchService.search("test", publicationId = "pub-5")
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.publicationId == "pub-5" })
    }

    @Test
    fun echappement_des_caracteres_speciaux_fts4() = runTest {
        listOf("test\"citation", "mot*", "a-b", "NEAR", "AND", "OR", "", "   ").forEach { query ->
            // Ne doit jamais lever d'exception SQLite, quel que soit l'entree -
            // toute la requete est enveloppee en phrase FTS4 (RoomSearchService.sanitizeFtsQuery).
            searchService.search(query)
        }
    }

    private companion object {
        const val SENTENCE_COUNT = 5000
    }
}
