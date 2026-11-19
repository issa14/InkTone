package com.inktone.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

/** Audit UI P0-13 : le surlignage suit la règle du moteur (accents et casse ignorés). */
class MatchRangesTest {

    @Test
    fun une_recherche_sans_accent_surligne_le_mot_accentue() {
        val text = "Un élève studieux"
        val ranges = matchRanges(text, "eleve")
        assertEquals(listOf(3..7), ranges)
        assertEquals("élève", text.substring(3, 8))
    }

    @Test
    fun une_recherche_accentuee_surligne_aussi_le_mot_sans_accent() {
        assertEquals(listOf(0..4), matchRanges("Eleve modèle", "Élève"))
    }

    @Test
    fun les_accents_decomposes_retombent_sur_les_indices_du_texte_affiche() {
        // « é » écrit e + accent combinant (deux caractères) : la plage couvre les deux.
        val text = "été"
        assertEquals(listOf(0..4), matchRanges(text, "ete"))
    }

    @Test
    fun chaque_occurrence_est_surlignee_et_moins_de_deux_caracteres_ne_surligne_rien() {
        assertEquals(listOf(0..1, 6..7), matchRanges("Ou et Où", "ou"))
        assertEquals(emptyList<IntRange>(), matchRanges("abc", "a"))
    }

    @Test
    fun une_apostrophe_droite_surligne_l_apostrophe_typographique() {
        assertEquals(listOf(0..11), matchRanges("N\u2019est-ce pas assez", "n'est-ce pas"))
        assertEquals(listOf(0..7), matchRanges("n'est-ce", "N\u2019est-ce"))
    }
}
