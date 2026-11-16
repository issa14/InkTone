package com.inktone.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class FrenchTypographyTest {

    @Test
    fun le_pourcentage_est_precede_d_une_espace_fine_insecable() {
        assertEquals("42 %", formatPercent(42))
        assertEquals("0 %", formatPercent(0))
    }
}
