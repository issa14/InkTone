package com.inktone.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contraste WCAG 2.x des schémas réellement appliqués ([InkToneLightColorScheme],
 * [InkToneDarkColorScheme]) — `PaletteContrastTest` ne vérifie que des
 * constantes de palette, pas les rôles que lisent les composants.
 */
class ColorSchemeContrastTest {

    private val schemes = mapOf("clair" to InkToneLightColorScheme, "sombre" to InkToneDarkColorScheme)

    /** Paires texte/fond : le texte posé sur chaque rôle de fond. */
    private fun textPairs(s: ColorScheme): Map<String, Pair<Color, Color>> = mapOf(
        "onPrimary/primary" to (s.primary to s.onPrimary),
        "onPrimaryContainer/primaryContainer" to (s.primaryContainer to s.onPrimaryContainer),
        "onSecondary/secondary" to (s.secondary to s.onSecondary),
        "onSecondaryContainer/secondaryContainer" to (s.secondaryContainer to s.onSecondaryContainer),
        "onTertiary/tertiary" to (s.tertiary to s.onTertiary),
        "onTertiaryContainer/tertiaryContainer" to (s.tertiaryContainer to s.onTertiaryContainer),
        "onError/error" to (s.error to s.onError),
        "onErrorContainer/errorContainer" to (s.errorContainer to s.onErrorContainer),
        "onBackground/background" to (s.background to s.onBackground),
        "onSurface/surface" to (s.surface to s.onSurface),
        "onSurfaceVariant/surface" to (s.surface to s.onSurfaceVariant),
    )

    /** Accents employés en texte (boutons texte, liens, onglets) sur les fonds. */
    private fun accentTextPairs(s: ColorScheme): Map<String, Pair<Color, Color>> = mapOf(
        "primary/background" to (s.background to s.primary),
        "primary/surface" to (s.surface to s.primary),
        "secondary/surface" to (s.surface to s.secondary),
        "tertiary/surface" to (s.surface to s.tertiary),
    )

    @Test
    fun chaque_texte_passe_aa_sur_son_role_de_fond() {
        schemes.forEach { (name, scheme) ->
            (textPairs(scheme) + accentTextPairs(scheme)).forEach { (pair, colors) ->
                val ratio = calculateContrastRatio(colors.first, colors.second)
                assertTrue("$name $pair : $ratio < 4.5", ratio >= 4.5)
            }
        }
    }

    @Test
    fun les_conteneurs_selectionnes_se_distinguent_du_fond() {
        // Puce, onglet ou élément de tiroir sélectionné : un conteneur M3 reste
        // proche de la surface (≈ 1,17:1) mais ne doit pas s'y confondre.
        schemes.forEach { (name, s) ->
            listOf("primaryContainer" to s.primaryContainer, "secondaryContainer" to s.secondaryContainer)
                .forEach { (role, color) ->
                    val ratio = calculateContrastRatio(s.surface, color)
                    assertTrue("$name $role/surface : $ratio < 1.1", ratio >= 1.1)
                }
        }
    }
}
