package com.inktone.domain.valueobject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocatorDisplayTest {

    @Test
    fun `une page de PDF est libellee Page, pas Chapitre`() {
        val locator = Locator(resourceHref = "page-30", chapterIndex = 30, charOffset = 0)
        assertTrue(locator.isFixedPage)
        assertEquals("Page 31", locator.positionLabel())
    }

    @Test
    fun `une ressource d'EPUB reste un chapitre, meme nommee comme une page`() {
        assertEquals("Chapitre 3", Locator("OEBPS/ch2.xhtml", 2, charOffset = 10).positionLabel())
        assertFalse(Locator("OEBPS/page-3.xhtml", 2, charOffset = 0).isFixedPage)
        assertFalse(Locator("page-3.xhtml", 2, charOffset = 0).isFixedPage)
    }
}
