package com.inktone.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ColorHexTest {

    @Test
    fun parse_un_hex_RRGGBB_en_couleur_opaque() {
        assertEquals(Color(0xFF112233), "#112233".toColor())
        assertEquals(Color.White, "#FFFFFF".toColor())
        assertEquals(Color.Black, "#000000".toColor())
    }

    @Test
    fun round_trip_couleur_vers_hex_vers_couleur() {
        val original = Color(0xFF4A90D9)
        assertEquals(original, original.toHex().toColor())
    }

    @Test
    fun un_hex_de_longueur_invalide_est_rejete() {
        assertThrows(IllegalArgumentException::class.java) { "#FFF".toColor() }
    }
}
