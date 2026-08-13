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
    fun `Chapter avec Rich content sans phrases`() {
        val block = BookBlock.ParagraphBlock(
            richText = StyledText.plain("Bonjour."),
            globalOffsetRange = 0..7,
        )
        val chapter = Chapter(
            index = 0,
            href = "chap1.xhtml",
            title = null,
            content = ChapterContent.Rich(blocks = listOf(block)),
        )
        assertEquals(0, chapter.sentences.size)
    }

    @Test
    fun `Chapter avec Rich content et phrases explicites`() {
        val block = BookBlock.ParagraphBlock(
            richText = StyledText.plain("Bonjour. Au revoir."),
            globalOffsetRange = 0..17,
        )
        val sentences = listOf(
            Sentence(index = 0, text = "Bonjour.", startOffset = 0, endOffset = 7, blockIndex = 0),
            Sentence(index = 1, text = "Au revoir.", startOffset = 9, endOffset = 17, blockIndex = 0),
        )
        val chapter = Chapter(
            index = 0,
            href = "chap1.xhtml",
            title = null,
            content = ChapterContent.Rich(blocks = listOf(block)),
            sentences = sentences,
        )
        assertEquals(2, chapter.sentences.size)
        assertEquals("Bonjour.", chapter.sentences[0].text)
        assertEquals(0, chapter.sentences[0].blockIndex)
    }
}
