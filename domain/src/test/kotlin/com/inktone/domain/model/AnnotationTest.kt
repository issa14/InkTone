package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator
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
