package com.inktone.feature.reader.rendering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FrenchElisionTest {

    @Test
    fun l_apostrophe_d_elision_devient_une_apostrophe_lettre() {
        assertEquals("pour lʼexistence, quʼil", "pour l’existence, qu’il".withUnbreakableElisions())
    }

    @Test
    fun la_longueur_est_conservee_pour_ne_decaler_aucun_offset() {
        val text = "L’homme d’aujourd’hui"
        assertEquals(text.length, text.withUnbreakableElisions().length)
    }

    @Test
    fun un_guillemet_fermant_hors_d_un_mot_reste_inchange() {
        assertEquals("‘mot’ suivant", "‘mot’ suivant".withUnbreakableElisions())
        assertEquals("fin’", "fin’".withUnbreakableElisions())
    }

    @Test
    fun un_texte_sans_apostrophe_typographique_n_est_pas_recopie() {
        val text = "l'existence"
        assertSame(text, text.withUnbreakableElisions())
    }
}
