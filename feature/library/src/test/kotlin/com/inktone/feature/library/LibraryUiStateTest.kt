package com.inktone.feature.library

import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tache 9bis.4 — recherche titre/auteur et tri sont derives client-cote
 * de `publications` (deja filtre par `PublicationRepository`), pas une
 * nouvelle requete Room (voir KDoc `LibraryUiState`).
 */
class LibraryUiStateTest {

    private fun publication(id: String, title: String, author: String, importDate: Long, lastOpened: Long? = null) = Publication(
        id = id, title = title, authors = listOf(author), format = PublicationFormat.EPUB,
        fileUri = "content://fake/$id", fileHash = "hash-$id", fileSize = 100L,
        chapterCount = 1, importDate = importDate, lastOpened = lastOpened,
    )

    @Test
    fun recherche_filtre_par_titre_ou_auteur_insensible_a_la_casse() {
        val state = LibraryUiState(
            publications = listOf(
                publication("1", "Les Misérables", "Victor Hugo", importDate = 0L),
                publication("2", "Germinal", "Émile Zola", importDate = 0L),
            ),
            searchQuery = "hugo",
        )

        assertEquals(listOf("1"), state.displayedPublications.map { it.id })
    }

    @Test
    fun tri_par_date_de_lecture_recente_ignore_les_jamais_ouverts() {
        val state = LibraryUiState(
            publications = listOf(
                publication("1", "A", "X", importDate = 0L, lastOpened = 100L),
                publication("2", "B", "X", importDate = 0L, lastOpened = null),
                publication("3", "C", "X", importDate = 0L, lastOpened = 200L),
            ),
            sortOrder = LibrarySortOrder.RECENTLY_OPENED,
        )

        assertEquals(listOf("3", "1", "2"), state.displayedPublications.map { it.id })
    }

    @Test
    fun carte_de_reprise_choisit_la_derniere_ouverture() {
        val state = LibraryUiState(
            publications = listOf(
                publication("1", "A", "X", importDate = 0L, lastOpened = 100L),
                publication("2", "B", "X", importDate = 0L, lastOpened = 300L),
                publication("3", "C", "X", importDate = 0L, lastOpened = null),
            ),
        )

        assertEquals("2", state.resumeReadingPublication?.id)
    }
}
