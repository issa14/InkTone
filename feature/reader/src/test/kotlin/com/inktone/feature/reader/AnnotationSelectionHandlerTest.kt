package com.inktone.feature.reader

import com.inktone.domain.model.Sentence
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
