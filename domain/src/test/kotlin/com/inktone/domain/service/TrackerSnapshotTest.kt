package com.inktone.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackerSnapshotTest {

    private fun snapshot(activeMs: Long) = TrackerSnapshot(visualMs = activeMs, ttsMs = 0, words = 0, sentences = 0)

    /**
     * Bug réel corrigé (audit UX D26) : le fragment repris le matin après une
     * nuit en arrière-plan était daté de la fin du fragment de la veille.
     */
    @Test
    fun un_fragment_apres_une_longue_pause_est_date_de_son_propre_debut() {
        val veilleAuSoir = 1_000_000L
        val lendemain = veilleAuSoir + 10 * 3_600_000L

        val start = snapshot(activeMs = 120_000).startedAt(endMs = lendemain, previousEndMs = veilleAuSoir)

        assertEquals(lendemain - 120_000, start)
    }

    @Test
    fun un_fragment_ne_commence_jamais_avant_la_fin_du_precedent() {
        val start = snapshot(activeMs = 600_000).startedAt(endMs = 1_300_000, previousEndMs = 1_000_000)

        assertEquals(1_000_000, start)
    }
}
