package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
