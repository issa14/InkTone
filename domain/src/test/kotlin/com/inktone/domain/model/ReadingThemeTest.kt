package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingThemeTest {

    @Test
    fun tous_les_ids_du_catalogue_integre_sont_uniques() {
        val ids = ReadingTheme.BUILT_IN.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun tous_les_themes_integres_sont_marques_isBuiltIn() {
        assertTrue(ReadingTheme.BUILT_IN.all { it.isBuiltIn })
    }

    @Test
    fun la_bascule_cyclique_du_lecteur_porte_exactement_trois_ambiances() {
        assertEquals(3, ReadingTheme.CYCLE.size)
        assertTrue(ReadingTheme.CYCLE.all { it.isBuiltIn })
    }

    @Test
    fun une_couleur_hors_format_RRGGBB_est_rejetee() {
        assertThrows(IllegalArgumentException::class.java) {
            ReadingTheme(
                id = "invalide", displayName = "Invalide", isBuiltIn = false,
                backgroundColorHex = "blanc", textColorHex = "#000000",
                accentColorHex = "#000000", highlightColorHex = "#000000",
                fontFamily = FontFamily.DEFAULT,
            )
        }
    }

    @Test
    fun un_nom_vide_est_rejete() {
        assertThrows(IllegalArgumentException::class.java) {
            ReadingTheme(
                id = "sans-nom", displayName = "", isBuiltIn = false,
                backgroundColorHex = "#FFFFFF", textColorHex = "#000000",
                accentColorHex = "#000000", highlightColorHex = "#000000",
                fontFamily = FontFamily.DEFAULT,
            )
        }
    }

    @Test
    fun le_defaut_est_un_theme_integre_de_la_section_ambiances() {
        assertTrue(ReadingTheme.DEFAULT.isBuiltIn)
        assertTrue(ReadingTheme.DEFAULT in ReadingTheme.AMBIANCES)
    }
}
