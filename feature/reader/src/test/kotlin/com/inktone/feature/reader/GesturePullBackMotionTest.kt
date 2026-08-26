package com.inktone.feature.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lot 21 — la réduction de mouvement supprime le rebond du geste de
 * tirage (garde-fou : personne ne doit réintroduire un `spring(...)` en
 * dur sans condition). La préférence applicative `reduceMotion` suffit à
 * annuler le rebond, indépendamment du réglage système.
 */
class GesturePullBackMotionTest {

    @Test
    fun reduceMotion_applicatif_annule_le_rebond_meme_si_le_systeme_anime() {
        assertFalse(pullBackIsElastic(reduceMotion = true, systemMotionReduced = false))
    }

    @Test
    fun reduceMotion_applicatif_annule_le_rebond_quand_le_systeme_est_deja_reduit() {
        assertFalse(pullBackIsElastic(reduceMotion = true, systemMotionReduced = true))
    }

    @Test
    fun reglage_systeme_seul_annule_le_rebond() {
        assertFalse(pullBackIsElastic(reduceMotion = false, systemMotionReduced = true))
    }

    @Test
    fun aucun_reglage_de_reduction_le_rebond_est_conserve() {
        assertTrue(pullBackIsElastic(reduceMotion = false, systemMotionReduced = false))
    }
}
