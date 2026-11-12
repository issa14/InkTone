package com.inktone.infrastructure.database.search

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.infrastructure.database.InkToneDatabase
import com.inktone.infrastructure.database.entity.PublicationEntity
import com.inktone.infrastructure.database.entity.SentenceFtsEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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

        // Les résultats ne portent que sur les livres présents dans la
        // bibliothèque : le corpus doit donc avoir ses livres en base.
        (0 until PUBLICATION_COUNT).forEach { insertPublication("pub-$it") }

        // Corpus synthetique de volume - plusieurs publications, texte
        // varie pour ne pas biaiser FTS4 vers un cas trivial (une seule
        // phrase repetee).
        val entities = (0 until SENTENCE_COUNT).map { index ->
            SentenceFtsEntity(
                publicationId = "pub-${index % PUBLICATION_COUNT}",
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

    @Test
    fun la_recherche_ne_porte_que_sur_le_texte_pas_sur_les_chemins_ni_les_identifiants() = runTest {
        // « chapter » et « xhtml » n'apparaissent que dans resourceHref,
        // « pub » que dans publicationId. Avant le correctif (MATCH sur la
        // table entière), ces trois recherches remontaient tout le corpus.
        assertTrue(searchService.search("chapter").isEmpty())
        assertTrue(searchService.search("xhtml").isEmpty())
        assertTrue(searchService.search("pub").isEmpty())
    }

    @Test
    fun les_phrases_d_un_livre_retire_de_la_bibliotheque_ne_sont_plus_proposees() = runTest {
        db.publicationDao().delete("pub-3")

        val results = searchService.search("imaginaire")

        assertTrue(results.isNotEmpty())
        assertTrue(results.none { it.publicationId == "pub-3" })
        assertTrue(searchService.search("imaginaire", publicationId = "pub-3").isEmpty())
    }

    @Test
    fun les_resultats_sont_bornes() = runTest {
        // Le terme figure dans les 5000 phrases du corpus.
        assertEquals(200, searchService.search("imaginaire").size)
    }

    @Test
    fun les_resultats_d_un_livre_suivent_l_ordre_de_lecture() = runTest {
        val results = searchService.search("imaginaire", publicationId = "pub-5")

        val positions = results.map { it.locator.chapterIndex to it.locator.charOffset }
        assertEquals(positions.sortedWith(compareBy({ it.first }, { it.second })), positions)
        // Tri numérique et non textuel : dans ce corpus, pub-5 porte les
        // offsets 5, 25, 45… 105 — un tri textuel placerait 105 avant 25.
        assertEquals(listOf(5, 25, 45, 65, 85, 105), positions.take(6).map { it.second })
    }

    @Test
    fun le_dernier_mot_tape_est_cherche_comme_debut_de_mot() = runTest {
        // « imagin » n'est pas un mot du corpus : sans recherche par début
        // de mot, rien ne s'affiche tant que le mot n'est pas complet.
        assertTrue(searchService.search("imagin").isNotEmpty())
        assertTrue(searchService.search("roman imagin").isNotEmpty())
        // Les mots qui précèdent le dernier restent exacts.
        assertTrue(searchService.search("rom imaginaire").isEmpty())
    }

    @Test
    fun une_requete_terminee_par_une_ponctuation_ne_leve_pas_d_exception() = runTest {
        listOf("imaginaire.", "test,", "roman !", "«", "*", "\"").forEach { query ->
            searchService.search(query)
        }
    }

    private suspend fun insertPublication(id: String) {
        db.publicationDao().insert(
            PublicationEntity(
                id = id, title = "Titre", subtitle = null, authors = emptyList(),
                publisher = null, language = null, description = null, coverUri = null,
                format = "EPUB", fileUri = "content://x/$id", fileHash = "h-$id", fileSize = 1L,
                chapterCount = 10, seriesName = null, seriesIndex = null, isFavorite = false,
                subjects = emptyList(), isDrmProtected = false, importDate = 0L, lastOpened = null,
            ),
        )
    }

    private companion object {
        const val SENTENCE_COUNT = 5000
        const val PUBLICATION_COUNT = 20
    }
}
