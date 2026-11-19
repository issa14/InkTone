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

    @Test
    fun les_decimales_prennent_la_virgule_quelle_que_soit_la_locale_du_systeme() {
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.US)
        try {
            assertEquals("1,59", formatDecimal(1.594, 2))
            assertEquals("1,59\u202FGo", formatFileSize(1_707_000_000L))
            assertEquals("183,0\u202FMo", formatFileSize(191_889_408L))
            assertEquals("12\u202FKo", formatFileSize(12_288L))
            assertEquals("512\u202Fo", formatFileSize(512L))
            assertEquals("0,75×", formatMultiplier(0.75f))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
