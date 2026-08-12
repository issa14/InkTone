package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RenderedPageTest {

    @Test
    fun `widthPx nul ou negatif est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            RenderedPage(widthPx = 0, heightPx = 10, pixelsArgb = IntArray(0))
        }
    }

    @Test
    fun `heightPx nul ou negatif est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            RenderedPage(widthPx = 10, heightPx = 0, pixelsArgb = IntArray(0))
        }
    }

    @Test
    fun `un tampon de pixels de taille incoherente est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            RenderedPage(widthPx = 10, heightPx = 10, pixelsArgb = IntArray(5))
        }
    }

    @Test
    fun `deux rendus avec les memes pixels sont egaux`() {
        val a = RenderedPage(widthPx = 2, heightPx = 2, pixelsArgb = intArrayOf(1, 2, 3, 4))
        val b = RenderedPage(widthPx = 2, heightPx = 2, pixelsArgb = intArrayOf(1, 2, 3, 4))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `deux rendus avec des pixels differents ne sont pas egaux`() {
        val a = RenderedPage(widthPx = 2, heightPx = 2, pixelsArgb = intArrayOf(1, 2, 3, 4))
        val b = RenderedPage(widthPx = 2, heightPx = 2, pixelsArgb = intArrayOf(1, 2, 3, 5))
        assertNotEquals(a, b)
    }
}
