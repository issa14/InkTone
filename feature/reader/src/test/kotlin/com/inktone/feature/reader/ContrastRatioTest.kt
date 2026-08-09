package com.inktone.feature.reader

import com.inktone.core.designsystem.calculateContrastRatio
import com.inktone.domain.model.ReadingTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tache 9.1.3 — mesure reelle du contraste des themes integres, pas une
 * supposition. WCAG AA : >= 4.5:1 texte normal.
 *
 * Lot 9 — la liste explicite d'enum est remplacee par ReadingTheme.BUILT_IN
 * (catalogue ouvert) : ce test couvre automatiquement tout thème intégré
 * ajouté au catalogue, pas seulement les quatre valeurs historiques.
 */
class ContrastRatioTest {

    @Test
    fun tous_les_themes_integres_respectent_le_contraste_wcag_aa() {
        ReadingTheme.BUILT_IN.forEach { theme ->
            val bg = ThemeColors.background(theme)
            val fg = ThemeColors.text(theme)
            val ratio = calculateContrastRatio(bg, fg)
            assertTrue("theme ${theme.id} : ratio $ratio < 4.5", ratio >= 4.5)
        }
    }

    @Test
    fun theme_sepia_vintage_mesure_specifiquement_au_dessus_du_seuil() {
        val ratio = calculateContrastRatio(ThemeColors.background(ReadingTheme.SEPIA_VINTAGE), ThemeColors.text(ReadingTheme.SEPIA_VINTAGE))
        // Mesure reelle (pas supposee) : texte sombre sur fond clair, largement au-dessus du seuil.
        assertTrue("ratio Sepia Vintage mesure : $ratio", ratio > 10.0)
    }
}
