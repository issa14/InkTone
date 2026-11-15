package com.inktone.data.mapper

import com.inktone.domain.model.ReadingState
import com.inktone.domain.valueobject.Locator
import org.junit.Assert.assertEquals
import org.junit.Test

class LocatorMapperTest {

    @Test
    fun `aller-retour Locator vers colonnes ne perd aucune information`() {
        val original = Locator(
            resourceHref = "ch3.xhtml", chapterIndex = 2, paragraphIndex = 5, charOffset = 142,
        )
        val roundTripped = original.toColumns().toLocator()
        assertEquals(original, roundTripped)
    }

    @Test
    fun `paragraphIndex nul est preserve a l'aller-retour`() {
        val original = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 0)
        assertEquals(original, original.toColumns().toLocator())
    }

    /** Audit UX D18 : le défilement dans la page PDF était perdu à l'écriture. */
    @Test
    fun `l'etat de lecture d'un PDF garde son defilement dans la page`() {
        val state = ReadingState(
            publicationId = "pub",
            locator = Locator(resourceHref = "page-7", chapterIndex = 7, charOffset = 0, pageOffsetY = 0.4f),
            lastReadAt = 1_000L,
        )
        assertEquals(state, state.toEntity().toDomain())
    }
}
