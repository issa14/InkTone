package com.inktone.feature.reader

import com.inktone.domain.model.TableOfContentsEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusLineSectionTest {
    private val toc = flattenTableOfContents(
        listOf(
            TableOfContentsEntry("Couverture", chapterIndex = 0),
            TableOfContentsEntry(
                "Tome I", chapterIndex = 1, fragment = "t1",
                children = listOf(
                    TableOfContentsEntry("Le départ", chapterIndex = 1, fragment = "c1"),
                    TableOfContentsEntry("La route", chapterIndex = 1, fragment = "c2"),
                ),
            ),
            TableOfContentsEntry("   ", chapterIndex = 2),
        ),
    )
    private val anchors = mapOf("t1" to 0, "c1" to 100, "c2" to 900)

    @Test
    fun la_section_est_celle_du_sommaire_et_non_le_numero_de_fichier() {
        val section = currentTocSection(toc, chapterIndex = 1, currentOffset = 500, anchors = anchors)
        assertEquals(TocSection("Le départ", startOffset = 100, endOffset = 900), section)
    }

    @Test
    fun la_derniere_section_va_jusqu_a_la_fin_du_chapitre() {
        val section = currentTocSection(toc, 1, currentOffset = 2000, anchors = anchors)
        assertEquals(TocSection("La route", 900, null), section)
    }

    @Test
    fun avant_toute_ancre_on_est_dans_la_premiere_section_du_chapitre() {
        assertEquals("Tome I", currentTocSection(toc, 1, currentOffset = 10, anchors = anchors)?.title)
    }

    @Test
    fun un_chapitre_sans_entree_ou_au_titre_vide_n_a_pas_de_section() {
        assertNull(currentTocSection(toc, 9, 0, anchors))
        assertNull(currentTocSection(toc, 2, 0, anchors))
    }

    @Test
    fun les_pages_de_la_section_sont_comptees_depuis_son_debut() {
        assertEquals(1 to 5, pageInSection(currentPageIndex = 10, pageIndexOfStart = 10, pageIndexOfLastChar = 14))
        assertEquals(3 to 5, pageInSection(12, 10, 14))
        assertEquals(1 to 1, pageInSection(currentPageIndex = 7, pageIndexOfStart = 9, pageIndexOfLastChar = 9))
    }

    @Test
    fun le_libelle_prefere_le_titre_de_section_au_numero_de_chapitre() {
        assertEquals("Le départ (3/5)", chapterCounterText(2, 3, 5, showPageCounter = true, sectionTitle = "Le départ"))
        assertEquals("Le départ", chapterCounterText(2, 3, 5, showPageCounter = false, sectionTitle = "Le départ"))
        assertEquals("Chapitre 2 (3/5)", chapterCounterText(2, 3, 5, showPageCounter = true))
    }
}
