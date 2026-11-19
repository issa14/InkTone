package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ExcerptDisplayTest {

    @Test
    fun un_extrait_sous_la_borne_reste_intact() {
        val text = "Une phrase complète."
        assertSame(text, text.excerptForDisplay(maxLength = 280))
    }

    @Test
    fun un_extrait_tronque_s_arrete_au_dernier_mot_entier_avec_une_ellipse() {
        assertEquals("Il était une fois un…", "Il était une fois un roya".excerptForDisplay(maxLength = 25))
    }

    @Test
    fun la_ponctuation_avant_la_coupe_est_retiree() {
        assertEquals("Le matin, la ville…", "Le matin, la ville, endo".excerptForDisplay(maxLength = 24))
    }

    @Test
    fun un_seul_mot_tres_long_est_coupe_tel_quel_avec_une_ellipse() {
        assertEquals("abcdefghij…", "abcdefghij".excerptForDisplay(maxLength = 10))
    }
}
