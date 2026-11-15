package com.inktone.domain.model

import com.inktone.domain.valueobject.LocatorText
import org.junit.Assert.assertEquals
import org.junit.Test

/** ADR-027 : le texte du chapitre dans l'espace de ses `charOffset`, pour capturer un contexte. */
class ChapterPlainTextTest {

    private fun chapter(vararg blocks: BookBlock, sentences: List<Sentence> = emptyList()) = Chapter(
        index = 0, href = "ch1.xhtml", title = null,
        content = ChapterContent.Rich(blocks.toList()), sentences = sentences,
    )

    @Test
    fun `les blocs sont places a leur offset, separes par un saut de ligne`() {
        val chapter = chapter(
            BookBlock.HeadingBlock(1, StyledText.plain("Titre"), 0..4),
            BookBlock.ParagraphBlock(StyledText.plain("Premier paragraphe."), 6..24),
            BookBlock.ImageBlock(href = "img.jpg"),
            BookBlock.ParagraphBlock(StyledText.plain("Second."), 26..32),
        )

        assertEquals("Titre\nPremier paragraphe.\nSecond.", chapter.plainText())
    }

    @Test
    fun `un chapitre sans texte donne un texte vide`() {
        assertEquals("", chapter(BookBlock.ImageBlock(href = "cover.jpg")).plainText())
    }

    @Test
    fun `le locator d'une phrase porte son contexte quand le texte du chapitre est fourni`() {
        val sentence = Sentence(index = 1, text = "Second.", startOffset = 26, endOffset = 32)
        val chapter = chapter(
            BookBlock.HeadingBlock(1, StyledText.plain("Titre"), 0..4),
            BookBlock.ParagraphBlock(StyledText.plain("Premier paragraphe."), 6..24),
            BookBlock.ParagraphBlock(StyledText.plain("Second."), 26..32),
            sentences = listOf(sentence),
        )

        val locator = sentence.startLocator(chapterIndex = 0, resourceHref = "ch1.xhtml", chapterText = chapter.plainText())

        assertEquals(26, locator.charOffset)
        assertEquals(LocatorText.capture(chapter.plainText(), 26), locator.text)
        assertEquals("Second.", locator.text?.after)
        assertEquals(null, sentence.startLocator(0, "ch1.xhtml").text)
    }
}
