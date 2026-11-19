package com.inktone.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import com.inktone.core.designsystem.OpenDyslexicFamily
import com.inktone.core.designsystem.SourceSerifFamily
import com.inktone.core.designsystem.calculateContrastRatio
import com.inktone.domain.model.EffectiveReadingSettings
import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.ReadingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lot 9 — ThemeColors dérive désormais du ReadingTheme résolu (couleurs hex), plus d'un enum fermé. */
class ThemeColorTest {

    @Test
    fun theme_obsidienne_a_un_fond_noir_et_un_texte_clair_adouci() {
        assertEquals(Color.Black, ThemeColors.background(ReadingTheme.OBSIDIENNE))
        assertContrasteAdouci(ReadingTheme.OBSIDIENNE)
    }

    @Test
    fun theme_sepia_vintage_utilise_un_fond_beige_et_un_texte_sombre() {
        assertEquals(Color(0xFFF4ECD8), ThemeColors.background(ReadingTheme.SEPIA_VINTAGE))
        assertNotEquals(Color.White, ThemeColors.text(ReadingTheme.SEPIA_VINTAGE))
    }

    @Test
    fun theme_papier_clair_a_un_fond_clair_et_un_texte_sombre_adoucis() {
        assertContrasteAdouci(ReadingTheme.PAPIER_CLAIR)
    }

    /** Audit UI P1-10 : très lisible (≥ 15:1) sans le 21:1 du noir et blanc purs. */
    private fun assertContrasteAdouci(theme: ReadingTheme) {
        val ratio = calculateContrastRatio(ThemeColors.background(theme), ThemeColors.text(theme))
        assertTrue("${theme.id} : $ratio", ratio in 15.0..20.0)
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

    // ───── Lot 21 — OpenDyslexic réellement rendue (tâche 1) ─────

    @Test
    fun toComposeFontFamily_rend_open_dyslexic_et_non_un_repli_sans_serif() {
        // Non-régression de l'écart intention↔code : le préréglage
        // d'accessibilité force OPEN_DYSLEXIC, qui doit produire la police
        // embarquée et JAMAIS un repli SansSerif silencieux.
        assertEquals(OpenDyslexicFamily, ThemeColors.toComposeFontFamily(FontFamily.OPEN_DYSLEXIC))
        assertNotEquals(ComposeFontFamily.SansSerif, ThemeColors.toComposeFontFamily(FontFamily.OPEN_DYSLEXIC))
    }

    // ───── Lot 21, tâche 10 — Source Serif 4 (police française à empattements) ─────

    @Test
    fun toComposeFontFamily_rend_source_serif_embarque_et_non_serif_systeme() {
        assertEquals(SourceSerifFamily, ThemeColors.toComposeFontFamily(FontFamily.SOURCE_SERIF))
        // La police embarquée est distincte du Serif système générique.
        assertNotEquals(ComposeFontFamily.Serif, ThemeColors.toComposeFontFamily(FontFamily.SOURCE_SERIF))
    }

    /** Audit UI P0-1 : le mot prononcé reste lisible dans chaque thème intégré. */
    @Test
    fun le_texte_reste_tres_lisible_sur_le_surlignage_du_mot_prononce() {
        for (theme in ReadingTheme.BUILT_IN) {
            val highlight = ThemeColors.wordHighlight(theme)
            val composed = androidx.compose.ui.graphics.lerp(
                ThemeColors.background(theme),
                highlight.copy(alpha = 1f),
                highlight.alpha,
            )
            val contrast = calculateContrastRatio(composed, ThemeColors.text(theme))
            assertTrue("${theme.id} : $contrast", contrast >= ThemeColors.WORD_HIGHLIGHT_MIN_CONTRAST)
        }
    }

    /** Une opacité basse rendrait le mot presque invisible sur fond clair. */
    @Test
    fun les_themes_clairs_gardent_un_surlignage_plein() {
        assertEquals(1f, ThemeColors.wordHighlight(ReadingTheme.PAPIER_CLAIR).alpha)
        assertEquals(1f, ThemeColors.wordHighlight(ReadingTheme.SEPIA_VINTAGE).alpha)
    }

    /** Audit UI P0-2 : commandes du HUD lisibles dans chaque thème intégré (3:1, élément d'interface). */
    @Test
    fun les_commandes_du_hud_restent_lisibles_dans_chaque_theme() {
        for (theme in ReadingTheme.BUILT_IN) {
            val bar = ThemeColors.barSurface(theme)
            val icon = androidx.compose.ui.graphics.lerp(bar, ThemeColors.barContent(theme), 0.87f)
            val accent = ThemeColors.accent(theme)
            assertTrue("${theme.id} icônes", calculateContrastRatio(icon, bar) >= 3.0)
            assertTrue("${theme.id} accent", calculateContrastRatio(accent, bar) >= 3.0)
            assertTrue("${theme.id} icône Lecture", calculateContrastRatio(ThemeColors.onAccent(theme), accent) >= 3.0)
        }
    }

    /** Chapitre en cours de la table des matières : texte du thème sur le conteneur sélectionné. */
    @Test
    fun le_texte_reste_lisible_sur_le_conteneur_selectionne_de_chaque_theme() {
        ReadingTheme.BUILT_IN.forEach { theme ->
            val ratio = calculateContrastRatio(ThemeColors.selectedContainer(theme), ThemeColors.barContent(theme))
            assertTrue("${theme.id} : $ratio < 4.5", ratio >= 4.5)
        }
    }

    /** Texte sélectionné : lisible sur le fond de sélection de chaque thème intégré. */
    @Test
    fun le_texte_reste_lisible_sous_la_selection_de_chaque_theme() {
        ReadingTheme.BUILT_IN.forEach { theme ->
            val selection = ThemeColors.selectionBackground(theme)
            val composed = androidx.compose.ui.graphics.lerp(ThemeColors.background(theme), selection.copy(alpha = 1f), selection.alpha)
            val ratio = calculateContrastRatio(composed, ThemeColors.text(theme))
            assertTrue("${theme.id} : $ratio < 4.5", ratio >= 4.5)
        }
    }
}
