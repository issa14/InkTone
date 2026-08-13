package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookBlockTest {

    @Test
    fun `ParagraphBlock a un globalOffsetRange non null`() {
        val block = BookBlock.ParagraphBlock(
            richText = StyledText.plain("Bonjour le monde."),
            globalOffsetRange = 0 until 18,
        )
        assertNotNull(block.globalOffsetRange)
        assertEquals(0, block.globalOffsetRange!!.first)
        assertEquals(17, block.globalOffsetRange!!.last) // IntRange.last est inclusif
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ParagraphBlock avec globalOffsetRange vide leve une exception`() {
        BookBlock.ParagraphBlock(
            richText = StyledText.plain(""),
            globalOffsetRange = IntRange.EMPTY,
        )
    }

    @Test
    fun `HeadingBlock a un niveau valide`() {
        val block = BookBlock.HeadingBlock(
            level = 1,
            richText = StyledText.plain("Titre"),
            globalOffsetRange = 0..4,
        )
        assertEquals(1, block.level)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `HeadingBlock niveau 0 leve une exception`() {
        BookBlock.HeadingBlock(
            level = 0,
            richText = StyledText.plain("Titre"),
            globalOffsetRange = 0..4,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `HeadingBlock niveau 7 leve une exception`() {
        BookBlock.HeadingBlock(
            level = 7,
            richText = StyledText.plain("Titre"),
            globalOffsetRange = 0..4,
        )
    }

    @Test
    fun `ImageBlock a globalOffsetRange null`() {
        val block = BookBlock.ImageBlock(href = "cover.png", alt = "Couverture")
        assertNull(block.globalOffsetRange)
    }

    @Test
    fun `ImageBlock conserve les dimensions intrinseques`() {
        val block = BookBlock.ImageBlock(
            href = "cover.png",
            alt = "Couverture",
            intrinsicWidth = 200,
            intrinsicHeight = 100,
        )
        assertEquals(200, block.intrinsicWidth)
        assertEquals(100, block.intrinsicHeight)
    }

    @Test
    fun `SeparatorBlock a globalOffsetRange null`() {
        val block = BookBlock.SeparatorBlock
        assertNull(block.globalOffsetRange)
    }

    @Test
    fun `approxByteSize est coherent entre les types de blocs`() {
        val paragraphBlock = BookBlock.ParagraphBlock(
            richText = StyledText.plain("Hello"),
            globalOffsetRange = 0..4,
        )
        val headingBlock = BookBlock.HeadingBlock(
            level = 1,
            richText = StyledText.plain("Hello"),
            globalOffsetRange = 0..4,
        )
        val imageBlock = BookBlock.ImageBlock(href = "img.png")
        val separatorBlock = BookBlock.SeparatorBlock

        // Tous les approxByteSize doivent être > 0
        assertTrue(paragraphBlock.approxByteSize > 0)
        assertTrue(headingBlock.approxByteSize > 0)
        assertTrue(imageBlock.approxByteSize > 0)
        assertTrue(separatorBlock.approxByteSize > 0)
    }
}
