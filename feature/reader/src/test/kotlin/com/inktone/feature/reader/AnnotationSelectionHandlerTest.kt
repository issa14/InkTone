package com.inktone.feature.reader

import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.StyledText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationSelectionHandlerTest {

    private val sentences = listOf(
        Sentence(0, "Bonjour le monde.", startOffset = 0, endOffset = 18),
        Sentence(1, "Ceci est un test.", startOffset = 19, endOffset = 37),
        Sentence(2, "Troisieme phrase.", startOffset = 38, endOffset = 55),
    )

    @Test
    fun `resolveCharRange produit un locator sur les offsets exacts, pas ceux de la phrase entiere`() {
        val sentence = sentences[0]
        val start = sentence.startOffset + sentence.text.indexOf("le")
        val end = start + "le".length
        val result = AnnotationSelectionHandler().resolveCharRange(start, end, 0, "ch1.xhtml")
        assertNotNull(result)
        assertEquals(start, result!!.first.charOffset)
        assertEquals(end, result.second.charOffset)
        // Le mot "le" ne commence pas au debut de la phrase : preuve que ce
        // n'est pas la phrase entiere qui est resolue.
        assertTrue(start > sentence.startOffset)
    }

    @Test
    fun `resolveCharRange retourne null si la borne de fin ne depasse pas le debut`() {
        assertNull(AnnotationSelectionHandler().resolveCharRange(10, 10, 0, "ch1.xhtml"))
        assertNull(AnnotationSelectionHandler().resolveCharRange(10, 5, 0, "ch1.xhtml"))
    }

    // ───── Lot 21, tâche 6 — paragraphIndex renseigné ─────

    @Test
    fun `resolveCharRange renseigne paragraphIndex sur une selection a cheval sur deux blocs`() {
        val blocks = listOf(
            BookBlock.ParagraphBlock(richText = StyledText.plain("Premier bloc."), globalOffsetRange = 0 until 14),
            BookBlock.ParagraphBlock(richText = StyledText.plain("Second bloc."), globalOffsetRange = 15 until 28),
        )
        // Début dans le bloc 0 (10), fin dans le bloc 1 (19 = dernier
        // caractère sélectionné, endOffsetExclusive = 20).
        val result = AnnotationSelectionHandler().resolveCharRange(10, 20, 0, "ch1.xhtml", blocks)
        assertNotNull(result)
        assertEquals(0, result!!.first.paragraphIndex)
        assertEquals(1, result.second.paragraphIndex)
        // charOffset reste l'ancre de vérité : résolu même avec paragraphIndex.
        assertEquals(10, result.first.charOffset)
        assertEquals(20, result.second.charOffset)
    }

    @Test
    fun `paragraphIndex de fin designe le dernier caractere selectionne, pas le bloc de charOffset`() {
        // charOffset (exclusif) tombe pile sur la frontière du bloc 1 —
        // dans le séparateur inter-blocs, hors de tout globalOffsetRange —
        // tandis que le DERNIER caractère réellement sélectionné (13)
        // appartient encore au bloc 0. paragraphIndex doit suivre ce
        // dernier caractère, pas charOffset : c'est la convention
        // documentée sur `endLocator` dans AnnotationSelectionHandler.
        val blocks = listOf(
            BookBlock.ParagraphBlock(richText = StyledText.plain("Premier bloc."), globalOffsetRange = 0 until 14),
            BookBlock.ParagraphBlock(richText = StyledText.plain("Second bloc."), globalOffsetRange = 15 until 28),
        )
        val result = AnnotationSelectionHandler().resolveCharRange(0, 14, 0, "ch1.xhtml", blocks)
        assertNotNull(result)
        assertEquals(14, result!!.second.charOffset)
        assertEquals(0, result.second.paragraphIndex)
    }

    @Test
    fun `resolveCharRange sans blocs conserve paragraphIndex nul pour les annotations existantes`() {
        val result = AnnotationSelectionHandler().resolveCharRange(10, 20, 0, "ch1.xhtml")
        assertNotNull(result)
        assertNull(result!!.first.paragraphIndex)
        assertNull(result.second.paragraphIndex)
    }

    @Test
    fun `sliceChapterText extrait la sous-chaine exacte a l'interieur d'une seule phrase`() {
        val sentence = sentences[0]
        val localStart = sentence.text.indexOf("monde")
        val start = sentence.startOffset + localStart
        val end = start + "monde".length
        assertEquals("monde", sliceChapterText(sentences, start, end))
    }

    @Test
    fun `sliceChapterText traverse plusieurs phrases sans planter sur le vide inter-phrase`() {
        val s0 = sentences[0]
        val s1 = sentences[1]
        val tail = s0.text.takeLast(6) // "monde."
        val head = s1.text.take(4) // "Ceci"
        val start = s0.startOffset + (s0.text.length - tail.length)
        val end = s1.startOffset + head.length
        assertEquals(tail + head, sliceChapterText(sentences, start, end))
    }

    @Test
    fun `sliceChapterText hors bornes retourne une chaine vide, pas une exception`() {
        assertEquals("", sliceChapterText(sentences, 1000, 1010))
    }
}
