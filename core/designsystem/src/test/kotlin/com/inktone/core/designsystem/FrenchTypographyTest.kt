package com.inktone.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class FrenchTypographyTest {

    @Test
    fun le_pourcentage_est_precede_d_une_espace_fine_insecable() {
        assertEquals("42\u202F%", formatPercent(42))
        assertEquals("0\u202F%", formatPercent(0))
    }

    @Test
    fun une_duree_s_ecrit_en_minutes_puis_en_heures_avec_minutes_sur_deux_chiffres() {
        assertEquals("0\u202Fmin", formatDurationMinutes(0))
        assertEquals("45\u202Fmin", formatDurationMinutes(45))
        assertEquals("1\u202Fh\u202F05", formatDurationMinutes(65))
        assertEquals("2\u202Fh\u202F00", formatDurationMinutes(120))
    }
}
