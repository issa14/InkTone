package com.inktone.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import com.inktone.domain.model.EffectiveReadingSettings
import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.ReadingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Lot 9 — ThemeColors dérive désormais du ReadingTheme résolu (couleurs hex), plus d'un enum fermé. */
class ThemeColorTest {

    @Test
    fun theme_obsidienne_utilise_un_fond_noir_et_un_texte_blanc() {
        assertEquals(Color.Black, ThemeColors.background(ReadingTheme.OBSIDIENNE))
        assertEquals(Color.White, ThemeColors.text(ReadingTheme.OBSIDIENNE))
    }

    @Test
    fun theme_sepia_vintage_utilise_un_fond_beige_et_un_texte_sombre() {
        assertEquals(Color(0xFFF4ECD8), ThemeColors.background(ReadingTheme.SEPIA_VINTAGE))
        assertNotEquals(Color.White, ThemeColors.text(ReadingTheme.SEPIA_VINTAGE))
    }

    @Test
    fun theme_papier_clair_a_un_fond_blanc_et_un_texte_noir() {
        assertEquals(Color.White, ThemeColors.background(ReadingTheme.PAPIER_CLAIR))
        assertEquals(Color.Black, ThemeColors.text(ReadingTheme.PAPIER_CLAIR))
    }

    @Test
    fun accent_et_surlignage_derivent_bien_de_leurs_propres_champs_hex() {
        val theme = ReadingTheme.PAPIER_CLAIR
        assertNotEquals(ThemeColors.background(theme), ThemeColors.accent(theme))
        assertNotEquals(ThemeColors.accent(theme), ThemeColors.highlight(theme))
    }

    // ───── Lot 9 — effectiveFontFamily (Tâche 9.2, piège pagination) ─────

    @Test
    fun la_preference_explicite_de_police_gagne_sur_celle_du_theme() {
        val settings = EffectiveReadingSettings(theme = ReadingTheme.OBSIDIENNE.id, fontSize = 18, fontFamily = FontFamily.OPEN_DYSLEXIC)
        assertEquals(FontFamily.OPEN_DYSLEXIC, ThemeColors.effectiveFontFamily(settings, ReadingTheme.OBSIDIENNE))
    }

    @Test
    fun sans_preference_explicite_la_police_du_theme_actif_s_applique() {
        val settings = EffectiveReadingSettings(theme = ReadingTheme.OBSIDIENNE.id, fontSize = 18, fontFamily = FontFamily.DEFAULT)
        assertEquals(ReadingTheme.OBSIDIENNE.fontFamily, ThemeColors.effectiveFontFamily(settings, ReadingTheme.OBSIDIENNE))
    }

    @Test
    fun toComposeFontFamily_distingue_bien_serif_et_sans_serif() {
        assertEquals(ComposeFontFamily.Serif, ThemeColors.toComposeFontFamily(FontFamily.SERIF))
        assertEquals(ComposeFontFamily.SansSerif, ThemeColors.toComposeFontFamily(FontFamily.SANS_SERIF))
        assertNotEquals(ThemeColors.toComposeFontFamily(FontFamily.SERIF), ThemeColors.toComposeFontFamily(FontFamily.SANS_SERIF))
    }
}
