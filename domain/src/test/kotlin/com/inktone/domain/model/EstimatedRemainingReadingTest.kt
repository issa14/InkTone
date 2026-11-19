package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimatedRemainingReadingTest {

    @Test
    fun le_temps_restant_est_proportionnel_au_temps_deja_passe() {
        assertEquals(3_600_000L, estimatedRemainingReadingMs(timeSpentMs = 2_400_000L, progression = 0.4f))
    }

    @Test
    fun aucune_estimation_sans_progression_ni_temps_passe() {
        assertNull(estimatedRemainingReadingMs(timeSpentMs = 2_400_000L, progression = null))
        assertNull(estimatedRemainingReadingMs(timeSpentMs = 2_400_000L, progression = 0f))
        assertNull(estimatedRemainingReadingMs(timeSpentMs = 0L, progression = 0.4f))
    }

    /** Audit UX P0-2 : La Guerre des mondes à 0,3 %, 26 min passées, affichait ≈ 146 h. */
    @Test
    fun a_faible_progression_l_estimation_repose_sur_le_texte_restant_et_la_vitesse() {
        val remaining = estimatedRemainingReadingMs(
            timeSpentMs = 26 * 60_000L, progression = 0.003f, totalChars = 360_000, averageWpm = 173,
        )!!
        assertTrue("$remaining ms", remaining in (5 * 60 + 30) * 60_000L..6 * 3_600_000L)
    }

    @Test
    fun a_faible_progression_sans_vitesse_mesuree_ni_longueur_aucune_estimation() {
        assertNull(estimatedRemainingReadingMs(26 * 60_000L, 0.003f, totalChars = 360_000, averageWpm = 0))
        assertNull(estimatedRemainingReadingMs(26 * 60_000L, 0.003f, totalChars = null, averageWpm = 173))
    }

    @Test
    fun au_dela_du_seuil_le_rythme_propre_au_livre_l_emporte() {
        assertEquals(
            3_600_000L,
            estimatedRemainingReadingMs(2_400_000L, 0.4f, totalChars = 360_000, averageWpm = 173),
        )
    }
}
