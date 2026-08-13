package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StyledTextTest {

    @Test
    fun `plain cree un StyledText sans spans`() {
        val st = StyledText.plain("Bonjour")
        assertEquals("Bonjour", st.plainText)
        assertTrue(st.spans.isEmpty())
    }

    @Test
    fun `approxByteSize est positif pour un texte non vide`() {
        val st = StyledText.plain("Hello World")
        assertTrue(st.approxByteSize > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `texte vide avec spans leve une exception`() {
        StyledText("", listOf(Span(SpanStyles.STRONG, 0, 1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `span hors bornes droites leve une exception`() {
        StyledText("abc", listOf(Span(SpanStyles.STRONG, 0, 5)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `span avec start negatif leve une exception`() {
        StyledText("abc", listOf(Span(SpanStyles.STRONG, -1, 2)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `spans chevauchants levent une exception`() {
        StyledText("abcdef", listOf(
            Span(SpanStyles.STRONG, 0, 4),
            Span(SpanStyles.EMPHASIS, 2, 6),
        ))
    }

    @Test
    fun `spans adjacents non chevauchants sont acceptes`() {
        val st = StyledText("abcdef", listOf(
            Span(SpanStyles.STRONG, 0, 3),
            Span(SpanStyles.EMPHASIS, 3, 6),
        ))
        assertEquals(2, st.spans.size)
    }

    @Test
    fun `approxByteSize tient compte des spans`() {
        val withoutSpans = StyledText.plain("Hello World")
        val withSpans = StyledText("Hello World", listOf(
            Span(SpanStyles.STRONG, 0, 5),
        ))
        assertTrue(withSpans.approxByteSize > withoutSpans.approxByteSize)
    }

    @Test
    fun `span avec reference porte un href`() {
        val span = Span(SpanStyles.REFERENCE, 0, 5, href = "http://example.com")
        assertEquals("http://example.com", span.href)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `span avec end inferieur ou egal a start leve une exception`() {
        Span(SpanStyles.STRONG, 5, 5)
    }
}
