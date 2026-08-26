package com.inktone.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lot 21, tâche 9 — mapping cran → vitesse d'auto-scroll (dp/s).
 * `0` = désactivé ; hors bornes = désactivé (le ViewModel borne déjà,
 * ce test fige le comportement du rendu).
 */
class AutoScrollTest {

    @Test
    fun vitesse_0_desactive() {
        assertEquals(0f, autoScrollDpPerSecond(0))
    }

    @Test
    fun les_crans_croissent_de_30_a_120() {
        assertEquals(30f, autoScrollDpPerSecond(1))
        assertEquals(60f, autoScrollDpPerSecond(2))
        assertEquals(120f, autoScrollDpPerSecond(3))
    }

    @Test
    fun vitesse_hors_bornes_desactive() {
        assertEquals(0f, autoScrollDpPerSecond(-1))
        assertEquals(0f, autoScrollDpPerSecond(99))
    }
}
