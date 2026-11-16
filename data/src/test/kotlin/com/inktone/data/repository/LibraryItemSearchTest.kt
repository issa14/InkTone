package com.inktone.data.repository

import com.inktone.domain.model.LibraryItem
import com.inktone.domain.model.LibraryItemType
import com.inktone.domain.valueobject.Locator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryItemSearchTest {
    private fun item(excerpt: String? = null, note: String? = null, title: String? = null) = LibraryItem(
        id = "1", type = LibraryItemType.NOTE, publicationId = "p", publicationTitle = title,
        startLocator = Locator(resourceHref = "c.xhtml", chapterIndex = 0, charOffset = 0), endLocator = null,
        color = null, excerpt = excerpt, note = note, isPinned = false, createdAt = 0L,
    )

    @Test
    fun la_recherche_ignore_accents_et_casse() {
        val needle = "eleve".foldedForSearch()
        assertTrue(item(excerpt = "Un Élève brillant").matches(needle))
        assertTrue(item(note = "À relire : élève").matches(needle))
        assertTrue(item(title = "ÉLÈVES").matches(needle))
        assertFalse(item(excerpt = "rien").matches(needle))
    }

    @Test
    fun les_jokers_sql_se_cherchent_tels_quels() {
        val needle = "50%".foldedForSearch()
        assertTrue(item(note = "soit 50% du total").matches(needle))
        assertFalse(item(note = "soit 500 du total").matches(needle))
    }

    @Test
    fun la_requete_est_rognee() {
        assertEquals("ecole", " École ".foldedForSearch())
    }
}
