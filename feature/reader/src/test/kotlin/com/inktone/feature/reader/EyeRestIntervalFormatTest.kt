package com.inktone.feature.reader

import com.inktone.core.designsystem.NARROW_NBSP
import org.junit.Assert.assertEquals
import org.junit.Test

/** Audit UI P2-11 : durées à la française, espace fine insécable avant l'unité. */
class EyeRestIntervalFormatTest {

    @Test
    fun minutes_heures_rondes_et_heures_et_minutes() {
        assertEquals("45${NARROW_NBSP}min", formatEyeRestInterval(45))
        assertEquals("1${NARROW_NBSP}h", formatEyeRestInterval(60))
        assertEquals("1${NARROW_NBSP}h${NARROW_NBSP}30", formatEyeRestInterval(90))
        assertEquals("2${NARROW_NBSP}h${NARROW_NBSP}05", formatEyeRestInterval(125))
    }
}
