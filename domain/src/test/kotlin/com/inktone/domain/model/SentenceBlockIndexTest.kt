package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceBlockIndexTest {

    @Test
    fun `Sentence avec blockIndex par defaut vaut -1`() {
        val sentence = Sentence(
            index = 0,
            text = "Bonjour.",
            startOffset = 0,
            endOffset = 7,
        )
        assertEquals(-1, sentence.blockIndex)
    }

    @Test
    fun `Sentence avec blockIndex explicite`() {
        val sentence = Sentence(
            index = 0,
            text = "Bonjour.",
            startOffset = 0,
            endOffset = 7,
            blockIndex = 2,
        )
        assertEquals(2, sentence.blockIndex)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Sentence avec blockIndex inferieur a -1 leve une exception`() {
        Sentence(
            index = 0,
            text = "Bonjour.",
            startOffset = 0,
            endOffset = 7,
            blockIndex = -2,
        )
    }

    @Test
    fun `Chapter avec Legacy content expose paragraphs via le getter deprecie`() {
        val chapter = Chapter(
            index = 0,
            href = "chap1.xhtml",
            title = null,
            content = ChapterContent.Legacy(
                paragraphs = listOf(
                    Paragraph(
                        index = 0,
                        sentences = listOf(
                            Sentence(0, "Bonjour.", 0, 7),
                        ),
                    ),
                ),
            ),
        )
        @Suppress("DEPRECATION")
        assertEquals(1, chapter.paragraphs.size)
        @Suppress("DEPRECATION")
        assertEquals("Bonjour.", chapter.paragraphs[0].sentences[0].text)
    }

    @Test
    fun `Chapter avec Rich content retourne une liste vide via le getter deprecie`() {
        val chapter = Chapter(
            index = 0,
            href = "chap1.xhtml",
            title = null,
            content = ChapterContent.Rich(blocks = emptyList()),
        )
        @Suppress("DEPRECATION")
        assertEquals(0, chapter.paragraphs.size)
        @Suppress("DEPRECATION")
        assertEquals(0, chapter.structuralBlocks.size)
    }
}
