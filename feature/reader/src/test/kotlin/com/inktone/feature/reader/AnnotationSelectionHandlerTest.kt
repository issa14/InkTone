package com.inktone.feature.reader

import com.inktone.domain.model.Sentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AnnotationSelectionHandlerTest {

    private val sentences = listOf(
        Sentence(0, "Bonjour le monde.", startOffset = 0, endOffset = 18),
        Sentence(1, "Ceci est un test.", startOffset = 19, endOffset = 37),
        Sentence(2, "Troisieme phrase.", startOffset = 38, endOffset = 55),
    )

    @Test
    fun `selection d'une seule phrase produit un locator sur ses bornes exactes`() {
        val result = AnnotationSelectionHandler().resolveSelection(
            sentences, startIndex = 0, endIndex = 0, chapterIndex = 0, resourceHref = "ch1.xhtml",
        )
        assertNotNull(result)
        assertEquals(0, result!!.first.charOffset)
        assertEquals(18, result.second.charOffset)
    }

    @Test
    fun `selection traversant plusieurs phrases est geree, pas rejetee silencieusement`() {
        val result = AnnotationSelectionHandler().resolveSelection(
            sentences, startIndex = 0, endIndex = 2, chapterIndex = 0, resourceHref = "ch1.xhtml",
        )
        assertNotNull("une selection multi-phrases doit produire une annotation, pas null", result)
        assertEquals(0, result!!.first.charOffset)
        assertEquals(55, result.second.charOffset)
    }

    @Test
    fun `ordre inverse (focus avant l'ancre) produit le meme resultat`() {
        val forward = AnnotationSelectionHandler().resolveSelection(sentences, 0, 2, 0, "ch1.xhtml")
        val backward = AnnotationSelectionHandler().resolveSelection(sentences, 2, 0, 0, "ch1.xhtml")
        assertEquals(forward, backward)
    }

    @Test
    fun `index hors bornes retourne null, pas une exception`() {
        val result = AnnotationSelectionHandler().resolveSelection(sentences, 0, 99, 0, "ch1.xhtml")
        assertNull(result)
    }
}
