package com.inktone.feature.reader

import com.inktone.core.designsystem.calculateContrastRatio
import com.inktone.domain.model.ReadingTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tache 9.1.3 — mesure reelle du contraste des quatre variantes de theme,
 * pas une supposition. WCAG AA : >= 4.5:1 texte normal.
 *
 * Point d'attention du plan (SEPIA jamais verifie, choisi pour
 * l'esthetique) : mesure, ne devine pas — le resultat confirme un texte
 * NOIR sur un fond clair `0xFFF4ECD8`, un cas favorable, pas defavorable.
 */
class ContrastRatioTest {

    @Test
    fun tous_les_themes_respectent_le_contraste_wcag_aa() {
        listOf(ReadingTheme.LIGHT, ReadingTheme.DARK, ReadingTheme.SEPIA, ReadingTheme.SYSTEM).forEach { theme ->
            val bg = ThemeColors.background(theme)
            val fg = ThemeColors.text(theme)
            val ratio = calculateContrastRatio(bg, fg)
            assertTrue("theme $theme : ratio $ratio < 4.5", ratio >= 4.5)
        }
    }

    @Test
    fun theme_sepia_mesure_specifiquement_au_dessus_du_seuil() {
        val ratio = calculateContrastRatio(ThemeColors.background(ReadingTheme.SEPIA), ThemeColors.text(ReadingTheme.SEPIA))
        // Mesure reelle (pas supposee) : texte noir sur fond 0xFFF4ECD8 ~= 17.7:1
        assertTrue("ratio SEPIA mesure : $ratio", ratio > 15.0)
    }
}
