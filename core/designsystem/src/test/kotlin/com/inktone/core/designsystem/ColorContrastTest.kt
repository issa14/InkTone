package com.inktone.core.designsystem

import androidx.compose.material3.ColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tache 9bis.1.1 — le legacy notait la palette "7/10 visuellement" sans
 * jamais faire tourner un vrai calcul de contraste (Tache 9.1.3, ecrite
 * depuis pour les themes de lecture). Meme discipline ici sur les
 * couleurs de chrome portees : mesure, pas impression visuelle.
 */
class ColorContrastTest {

    private fun assertPairPassesWcagAa(scheme: ColorScheme, background: androidx.compose.ui.graphics.Color, foreground: androidx.compose.ui.graphics.Color, label: String) {
        val ratio = calculateContrastRatio(background, foreground)
        assertTrue("$label : ratio $ratio < 4.5", ratio >= 4.5)
    }

    @Test
    fun palette_claire_respecte_le_contraste_wcag_aa_sur_les_paires_texte() {
        val scheme = InkToneLightColorScheme
        assertPairPassesWcagAa(scheme, scheme.background, scheme.onBackground, "light background/onBackground")
        assertPairPassesWcagAa(scheme, scheme.surface, scheme.onSurface, "light surface/onSurface")
        assertPairPassesWcagAa(scheme, scheme.surfaceVariant, scheme.onSurfaceVariant, "light surfaceVariant/onSurfaceVariant")
        assertPairPassesWcagAa(scheme, scheme.primary, scheme.onPrimary, "light primary/onPrimary")
        assertPairPassesWcagAa(scheme, scheme.secondary, scheme.onSecondary, "light secondary/onSecondary")
        assertPairPassesWcagAa(scheme, scheme.tertiary, scheme.onTertiary, "light tertiary/onTertiary")
        assertPairPassesWcagAa(scheme, scheme.error, scheme.onError, "light error/onError")
    }

    @Test
    fun palette_sombre_respecte_le_contraste_wcag_aa_sur_les_paires_texte() {
        val scheme = InkToneDarkColorScheme
        assertPairPassesWcagAa(scheme, scheme.background, scheme.onBackground, "dark background/onBackground")
        assertPairPassesWcagAa(scheme, scheme.surface, scheme.onSurface, "dark surface/onSurface")
        assertPairPassesWcagAa(scheme, scheme.surfaceVariant, scheme.onSurfaceVariant, "dark surfaceVariant/onSurfaceVariant")
        assertPairPassesWcagAa(scheme, scheme.primary, scheme.onPrimary, "dark primary/onPrimary")
        assertPairPassesWcagAa(scheme, scheme.secondary, scheme.onSecondary, "dark secondary/onSecondary")
        assertPairPassesWcagAa(scheme, scheme.tertiary, scheme.onTertiary, "dark tertiary/onTertiary")
        assertPairPassesWcagAa(scheme, scheme.error, scheme.onError, "dark error/onError")
    }
}
