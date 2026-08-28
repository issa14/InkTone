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
        ).withDerivedFields()

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
        ).withDerivedFields()

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
        ).withDerivedFields()

        assertEquals("2", state.resumeReadingPublication?.id)
    }

    @Test
    fun tri_par_auteur_ignore_la_casse_et_place_les_auteurs_absents_en_fin() {
        val state = LibraryUiState(
            publications = listOf(
                publication("1", "A", "zola", importDate = 0L),
                publication("2", "B", "Hugo", importDate = 0L),
                publication("3", "C", "", importDate = 0L).copy(authors = emptyList()),
            ),
            sortOrder = LibrarySortOrder.AUTHOR,
        ).withDerivedFields()

        assertEquals(listOf("2", "1", "3"), state.displayedPublications.map { it.id })
    }

    @Test
    fun filtre_par_format_vide_n_exclut_rien() {
        val epub = publication("1", "A", "X", importDate = 0L)
        val txt = publication("2", "B", "X", importDate = 0L).copy(format = PublicationFormat.TXT)
        val state = LibraryUiState(publications = listOf(epub, txt)).withDerivedFields()

        assertEquals(listOf("1", "2"), state.displayedPublications.map { it.id })
    }

    @Test
    fun filtre_par_format_epub_seul_masque_les_txt() {
        val epub = publication("1", "A", "X", importDate = 0L)
        val txt = publication("2", "B", "X", importDate = 0L).copy(format = PublicationFormat.TXT)
        val state = LibraryUiState(
            publications = listOf(epub, txt),
            selectedFormats = setOf(PublicationFormat.EPUB),
        ).withDerivedFields()

        assertEquals(listOf("1"), state.displayedPublications.map { it.id })
    }

    @Test
    fun un_livre_epingle_remonte_en_tete_quel_que_soit_le_tri() {
        val state = LibraryUiState(
            publications = listOf(
                publication("1", "A", "X", importDate = 300L),
                publication("2", "B", "X", importDate = 200L).copy(isPinned = true),
                publication("3", "C", "X", importDate = 100L),
            ),
            sortOrder = LibrarySortOrder.RECENTLY_ADDED,
        ).withDerivedFields()

        assertEquals(listOf("2", "1", "3"), state.displayedPublications.map { it.id })
    }
}
