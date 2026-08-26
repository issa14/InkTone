package com.inktone.feature.reader.rendering

import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.valueobject.Locator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Lot 23, tâche 13 — `annotationAtOffset` pilote la détection de tap sur
 * une annotation existante (tâche 11) : bornes demi-ouvertes
 * `[startLocator, endLocator[`, jamais une autre publication ou un autre
 * chapitre.
 */
class BookBlockItemTest {

    private fun locator(chapterIndex: Int, offset: Int) =
        Locator(resourceHref = "ch$chapterIndex.xhtml", chapterIndex = chapterIndex, charOffset = offset)

    private fun annotation(chapterIndex: Int, start: Int, end: Int) = Annotation(
        id = "an-1", publicationId = "pub-1",
        startLocator = locator(chapterIndex, start), endLocator = locator(chapterIndex, end),
        color = AnnotationColor.YELLOW, createdAt = 0L, updatedAt = 0L,
    )

    @Test
    fun `un offset dans la plage trouve l'annotation`() {
        val a = annotation(chapterIndex = 0, start = 10, end = 20)
        assertEquals(a, annotationAtOffset(listOf(a), chapterIndex = 0, globalOffset = 15))
    }

    @Test
    fun `la borne de depart est incluse`() {
        val a = annotation(chapterIndex = 0, start = 10, end = 20)
        assertEquals(a, annotationAtOffset(listOf(a), chapterIndex = 0, globalOffset = 10))
    }

    @Test
    fun `la borne de fin est exclue`() {
        val a = annotation(chapterIndex = 0, start = 10, end = 20)
        assertNull(annotationAtOffset(listOf(a), chapterIndex = 0, globalOffset = 20))
    }

    @Test
    fun `un autre chapitre ne matche jamais`() {
        val a = annotation(chapterIndex = 0, start = 10, end = 20)
        assertNull(annotationAtOffset(listOf(a), chapterIndex = 1, globalOffset = 15))
    }

    @Test
    fun `un offset hors plage retourne null`() {
        val a = annotation(chapterIndex = 0, start = 10, end = 20)
        assertNull(annotationAtOffset(listOf(a), chapterIndex = 0, globalOffset = 25))
    }
}
