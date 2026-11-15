package com.inktone.feature.reader

import com.inktone.domain.model.TableOfContentsEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentTocEntryTest {
    private val entries = listOf(
        TableOfContentsEntry("Couverture", chapterIndex = 0),
        TableOfContentsEntry("Partie 1", chapterIndex = 1, fragment = "p1"),
        TableOfContentsEntry("Partie 2", chapterIndex = 1, fragment = "p2"),
        TableOfContentsEntry("Partie 3", chapterIndex = 1, fragment = "p3"),
        TableOfContentsEntry("Suite", chapterIndex = 2),
    )
    private val anchors = mapOf("p1" to 0, "p2" to 500, "p3" to 1200)

    @Test
    fun une_seule_entree_est_courante_parmi_celles_d_un_meme_fichier() {
        assertEquals(1, currentTocEntryIndex(entries, 1, currentOffset = 100, anchors = anchors))
        assertEquals(2, currentTocEntryIndex(entries, 1, currentOffset = 500, anchors = anchors))
        assertEquals(3, currentTocEntryIndex(entries, 1, currentOffset = 5000, anchors = anchors))
    }

    @Test
    fun une_ancre_inconnue_vaut_le_debut_du_chapitre() {
        assertEquals(3, currentTocEntryIndex(entries, 1, currentOffset = 10, anchors = emptyMap()))
    }

    @Test
    fun une_entree_sans_fragment_est_courante_pour_son_chapitre() {
        assertEquals(4, currentTocEntryIndex(entries, 2, currentOffset = 42, anchors = anchors))
    }

    @Test
    fun un_chapitre_absent_du_sommaire_n_a_pas_d_entree_courante() {
        assertNull(currentTocEntryIndex(entries, 9, currentOffset = 0, anchors = anchors))
    }

    @Test
    fun avant_la_premiere_ancre_on_retombe_sur_la_premiere_entree_du_chapitre() {
        val late = mapOf("p1" to 300, "p2" to 500, "p3" to 1200)
        assertEquals(1, currentTocEntryIndex(entries, 1, currentOffset = 10, anchors = late))
    }
}
