package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Calcul UNIQUE de la progression (Lecteur, Bibliothèque, Récents,
 * Statistiques) : position dans le texte du livre entier.
 */
class ReadingProgressTest {

    private fun locator(chapterIndex: Int, charOffset: Int, pageOffsetY: Float? = null) =
        Locator(resourceHref = "ch$chapterIndex.xhtml", chapterIndex = chapterIndex, charOffset = charOffset, pageOffsetY = pageOffsetY)

    private fun publication(
        format: PublicationFormat = PublicationFormat.EPUB,
        chapterCount: Int = 3,
        chapterLengths: ChapterLengths? = ChapterLengths(listOf(0, 100, 300)),
    ) = Publication(
        id = "pub", title = "Titre", format = format, fileUri = "content://pub", fileHash = "hash",
        fileSize = 1L, chapterCount = chapterCount, chapterLengths = chapterLengths, importDate = 0L,
    )

    @Test
    fun `la progression compte la position dans le chapitre et la taille des chapitres`() {
        // Couverture vide (0), chapitre de 100, chapitre de 300 : milieu du
        // dernier chapitre = (100 + 150) / 400.
        assertEquals(250f / 400f, publication().progressionAt(locator(2, 150))!!, 0.0001f)
    }

    @Test
    fun `le debut du dernier chapitre n'est pas 100 pourcent`() {
        // L'ancien calcul de la Bibliothèque (chapterIndex / (chapterCount - 1))
        // affichait 100 % ici.
        assertEquals(100f / 400f, publication().progressionAt(locator(2, 0))!!, 0.0001f)
    }

    @Test
    fun `un offset au-dela de la fin du chapitre ne deborde pas sur le suivant`() {
        assertEquals(100f / 400f, publication().progressionAt(locator(1, 5_000))!!, 0.0001f)
    }

    @Test
    fun `longueurs inconnues - progression inconnue, jamais estimee`() {
        assertNull(publication(chapterLengths = null).progressionAt(locator(1, 10)))
    }

    @Test
    fun `livre sans texte - progression inconnue`() {
        assertNull(publication(chapterLengths = ChapterLengths(listOf(0, 0, 0))).progressionAt(locator(1, 0)))
    }

    @Test
    fun `chapitre hors bornes - progression inconnue`() {
        assertNull(publication().progressionAt(locator(7, 0)))
    }

    @Test
    fun `PDF - progression en pages, defilement dans la page compris`() {
        val pdf = publication(format = PublicationFormat.PDF, chapterCount = 10, chapterLengths = null)
        assertEquals(4.5f / 10f, pdf.progressionAt(locator(4, 0, pageOffsetY = 0.5f))!!, 0.0001f)
    }

    @Test
    fun `les longueurs se mesurent a la fin de la derniere phrase de chaque chapitre`() {
        val chapters = listOf(
            Chapter(index = 0, href = "cover.xhtml", title = null, content = ChapterContent.Rich(emptyList())),
            Chapter(
                index = 1, href = "ch1.xhtml", title = null, content = ChapterContent.Rich(emptyList()),
                sentences = listOf(
                    Sentence(index = 0, text = "Un.", startOffset = 0, endOffset = 3),
                    // Offsets dans le texte du chapitre, blancs compris : la
                    // longueur suit cet espace, pas la somme des textes.
                    Sentence(index = 1, text = "Deux.", startOffset = 4, endOffset = 9),
                ),
            ),
        )
        assertEquals(listOf(0, 9), ChapterLengths.of(chapters).values)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `des longueurs qui ne comptent pas tous les chapitres sont refusees`() {
        publication(chapterCount = 3, chapterLengths = ChapterLengths(listOf(10, 20)))
    }
}
