package com.inktone.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatMultiplierTest {

    /** Audit UX B9 : un cran de 0,25 s'affichait arrondi à une décimale (0,75 → « 0,8× »). */
    @Test
    fun deux_decimales_seulement_quand_la_valeur_en_a_besoin() {
        assertEquals("0,75×", formatMultiplier(0.75f))
        assertEquals("1,0×", formatMultiplier(1.0f))
        assertEquals("1,5×", formatMultiplier(1.5f))
    }
}
