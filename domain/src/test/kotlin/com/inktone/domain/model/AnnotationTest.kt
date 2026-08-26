package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AnnotationTest {

    private fun locator(offset: Int) = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = offset)

    @Test
    fun `endLocator anterieur a startLocator est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            Annotation(
                id = "a1", publicationId = "pub-1",
                startLocator = locator(200), endLocator = locator(50),
                color = AnnotationColor.YELLOW,
                createdAt = 0L, updatedAt = 0L,
            )
        }
    }

    @Test
    fun `une plage de longueur nulle (start egal end) est valide`() {
        // Ne doit pas lever d'exception — surlignage ponctuel valide.
        Annotation(
            id = "a1", publicationId = "pub-1",
            startLocator = locator(100), endLocator = locator(100),
            color = AnnotationColor.YELLOW,
            createdAt = 0L, updatedAt = 0L,
        )
    }
}

class AnnotationColorTest {

    @Test
    fun `un aller-retour hex preserve la couleur`() {
        val custom = AnnotationColor(0xFF123456.toInt())
        assertEquals(custom, AnnotationColor.parse(custom.toHex()))
    }

    @Test
    fun `un nom d'enum herite se decode vers le meme hex qu'avant le Lot 23`() {
        assertEquals("#FFFFF59D", AnnotationColor.parse("YELLOW").toHex())
        assertEquals("#FFA5D6A7", AnnotationColor.parse("GREEN").toHex())
        assertEquals("#FF90CAF9", AnnotationColor.parse("BLUE").toHex())
        assertEquals("#FFF48FB1", AnnotationColor.parse("PINK").toHex())
        assertEquals("#FFFFCC80", AnnotationColor.parse("ORANGE").toHex())
    }

    @Test
    fun `PRESETS contient les 5 couleurs historiques dans leur ordre d'origine`() {
        assertEquals(
            listOf(AnnotationColor.YELLOW, AnnotationColor.GREEN, AnnotationColor.BLUE, AnnotationColor.PINK, AnnotationColor.ORANGE),
            AnnotationColor.PRESETS,
        )
    }
}
