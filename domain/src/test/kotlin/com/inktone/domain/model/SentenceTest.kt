package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SentenceTest {

    @Test
    fun `startLocator utilise l'offset de debut, pas de fin`() {
        val sentence = Sentence(index = 2, text = "Bonjour le monde.", startOffset = 150, endOffset = 168)
        val locator = sentence.startLocator(chapterIndex = 3, resourceHref = "ch3.xhtml")
        assertEquals(
            Locator(resourceHref = "ch3.xhtml", chapterIndex = 3, charOffset = 150),
            locator,
        )
    }

    @Test
    fun `endOffset inferieur a startOffset est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            Sentence(index = 0, text = "x", startOffset = 100, endOffset = 50)
        }
    }
}
