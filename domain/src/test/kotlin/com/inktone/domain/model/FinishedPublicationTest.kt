package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit UX B2, D31 — « terminé » suit la progression affichée, plus l'index
 * de chapitre.
 */
class FinishedPublicationTest {

    private fun book(chapterLengths: ChapterLengths?, chapterCount: Int, format: PublicationFormat = PublicationFormat.EPUB) = Publication(
        id = "pub", title = "Titre", format = format, fileUri = "content://pub", fileHash = "h", fileSize = 1,
        chapterCount = chapterCount, chapterLengths = chapterLengths, importDate = 0L,
    )

    @Test
    fun un_livre_a_un_seul_chapitre_n_est_pas_termine_des_son_ouverture() {
        val txt = book(ChapterLengths(listOf(10_000)), chapterCount = 1, format = PublicationFormat.TXT)

        assertFalse(txt.isFinishedAt(Locator("txt", chapterIndex = 0, charOffset = 0)))
        assertTrue(txt.isFinishedAt(Locator("txt", chapterIndex = 0, charOffset = 9_900)))
    }

    @Test
    fun le_debut_d_un_long_dernier_chapitre_n_est_pas_la_fin_du_livre() {
        val epub = book(ChapterLengths(listOf(1_000, 1_000, 8_000)), chapterCount = 3)

        assertFalse(epub.isFinishedAt(Locator("ch2", chapterIndex = 2, charOffset = 100)))
    }

    @Test
    fun progression_inconnue_jamais_terminee() {
        assertFalse(book(chapterLengths = null, chapterCount = 3).isFinishedAt(Locator("ch2", chapterIndex = 2, charOffset = 999)))
    }

    @Test
    fun un_pdf_est_termine_a_sa_derniere_page() {
        val pdf = book(chapterLengths = null, chapterCount = 10, format = PublicationFormat.PDF)

        assertFalse(pdf.isFinishedAt(Locator("page-8", chapterIndex = 8, charOffset = 0)))
        assertTrue(pdf.isFinishedAt(Locator("page-9", chapterIndex = 9, charOffset = 0)))
    }
}
