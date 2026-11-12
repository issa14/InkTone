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

    @Test
    fun tri_par_titre_range_les_lettres_accentuees_a_leur_place_alphabetique() {
        // Ordre binaire d'avant : « À rebours » et « Émaux et camées »
        // passaient après « Zadig », toute lettre accentuée venant après z.
        val state = LibraryUiState(
            publications = listOf(
                publication("1", "Zadig", "X", importDate = 0L),
                publication("2", "Émaux et camées", "X", importDate = 0L),
                publication("3", "À rebours", "X", importDate = 0L),
                publication("4", "Bel-Ami", "X", importDate = 0L),
            ),
            sortOrder = LibrarySortOrder.TITLE,
        ).withDerivedFields()

        assertEquals(listOf("3", "4", "2", "1"), state.displayedPublications.map { it.id })
    }

    @Test
    fun tri_par_auteur_range_les_lettres_accentuees_a_leur_place_alphabetique() {
        val state = LibraryUiState(
            publications = listOf(
                publication("1", "A", "Zola", importDate = 0L),
                publication("2", "B", "Éluard", importDate = 0L),
                publication("3", "C", "Balzac", importDate = 0L),
            ),
            sortOrder = LibrarySortOrder.AUTHOR,
        ).withDerivedFields()

        assertEquals(listOf("3", "2", "1"), state.displayedPublications.map { it.id })
    }

    @Test
    fun recherche_insensible_aux_accents() {
        val state = LibraryUiState(
            publications = listOf(
                publication("1", "L'Élève", "X", importDate = 0L),
                publication("2", "Germinal", "Émile Zola", importDate = 0L),
                publication("3", "Autre", "Y", importDate = 0L),
            ),
            searchQuery = "ele",
        ).withDerivedFields()

        assertEquals(listOf("1"), state.displayedPublications.map { it.id })
        val byAuthor = state.copy(searchQuery = "emile").withDerivedFields()
        assertEquals(listOf("2"), byAuthor.displayedPublications.map { it.id })
    }

    @Test
    fun series_et_tags_du_menu_suivent_l_ordre_alphabetique_francais() {
        val state = LibraryUiState(
            publications = listOf(
                publication("1", "A", "X", importDate = 0L).copy(seriesName = "Zodiaque", subjects = listOf("Érotisme", "Aventure")),
                publication("2", "B", "X", importDate = 0L).copy(seriesName = "Étoiles", subjects = listOf("Zen")),
            ),
        ).withDerivedFields()

        assertEquals(listOf("Étoiles", "Zodiaque"), state.availableSeries)
        assertEquals(listOf("Aventure", "Érotisme", "Zen"), state.availableTags)
    }
}
