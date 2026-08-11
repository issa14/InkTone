package com.inktone.domain.valueobject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocatorTest {

    @Test
    fun `deux locators du meme chapitre s'ordonnent par offset`() {
        val early = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 10)
        val late = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 200)
        assertTrue(early < late)
    }

    @Test
    fun `un locator de chapitre ulterieur est toujours superieur, meme avec un offset plus petit`() {
        val chapter0 = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 5000)
        val chapter1 = Locator(resourceHref = "ch2.xhtml", chapterIndex = 1, charOffset = 0)
        assertTrue(chapter1 > chapter0)
    }

    @Test
    fun `resourceHref vide est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            Locator(resourceHref = "", chapterIndex = 0, charOffset = 0)
        }
    }

    @Test
    fun `chapterIndex negatif est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            Locator(resourceHref = "ch1.xhtml", chapterIndex = -1, charOffset = 0)
        }
    }

    @Test
    fun `charOffset negatif est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = -1)
        }
    }

    @Test
    fun `progression est calculee et bornee entre 0 et 1`() {
        val locator = Locator(resourceHref = "ch2.xhtml", chapterIndex = 1, charOffset = 500)
        val progression = Locator.computeProgression(
            locator = locator,
            totalCharsBeforeChapter = 10_000,
            totalCharsInPublication = 20_000,
        )
        assertEquals(0.525f, progression, 0.001f)
    }

    @Test
    fun `progression ne depasse jamais 1 meme avec un offset aberrant`() {
        val locator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 999_999)
        val progression = Locator.computeProgression(
            locator = locator, totalCharsBeforeChapter = 0, totalCharsInPublication = 1000,
        )
        assertEquals(1f, progression, 0.001f)
    }

    @Test
    fun `pageOffsetY hors bornes est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            Locator(resourceHref = "page-0", chapterIndex = 0, charOffset = 0, pageOffsetY = 1.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Locator(resourceHref = "page-0", chapterIndex = 0, charOffset = 0, pageOffsetY = -0.1f)
        }
    }

    @Test
    fun `pageOffsetY absent ou dans les bornes est valide`() {
        Locator(resourceHref = "page-0", chapterIndex = 0, charOffset = 0)
        Locator(resourceHref = "page-0", chapterIndex = 0, charOffset = 0, pageOffsetY = 0f)
        Locator(resourceHref = "page-0", chapterIndex = 0, charOffset = 0, pageOffsetY = 1f)
    }

    @Test
    fun `progression est nulle si la publication n'a aucun caractere connu`() {
        val locator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 0)
        val progression = Locator.computeProgression(
            locator = locator, totalCharsBeforeChapter = 0, totalCharsInPublication = 0,
        )
        assertEquals(0f, progression, 0.001f)
    }
}
