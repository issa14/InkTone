package com.inktone.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Audit UI P0-5 : sans couleurs dynamiques, un rôle `surfaceContainer*` non
 * défini retombe sur la base Material (lavande) — feuilles et cartes hors
 * charte sur le fond de la marque.
 */
class SurfaceContainerRolesTest {

    private fun roles(scheme: ColorScheme) = mapOf(
        "surfaceDim" to scheme.surfaceDim,
        "surfaceBright" to scheme.surfaceBright,
        "surfaceContainerLowest" to scheme.surfaceContainerLowest,
        "surfaceContainerLow" to scheme.surfaceContainerLow,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        "surfaceContainerHighest" to scheme.surfaceContainerHighest,
    )

    @Test
    fun le_schema_clair_definit_tous_les_roles_de_surface() {
        val baseline = roles(lightColorScheme())
        roles(InkToneLightColorScheme).forEach { (name, color) ->
            assertNotEquals(name, baseline.getValue(name), color)
        }
    }

    @Test
    fun le_schema_sombre_definit_tous_les_roles_de_surface() {
        val baseline = roles(darkColorScheme())
        roles(InkToneDarkColorScheme).forEach { (name, color) ->
            assertNotEquals(name, baseline.getValue(name), color)
        }
    }

    /** Audit UI P0-10 : `primary` sert de couleur de texte (boutons texte, liens), il doit tenir 4,5:1. */
    @Test
    fun primary_reste_lisible_comme_texte_sur_les_surfaces() {
        for (scheme in listOf(InkToneLightColorScheme, InkToneDarkColorScheme)) {
            for (surface in listOf(scheme.background, scheme.surface, scheme.surfaceContainerHigh)) {
                val ratio = calculateContrastRatio(surface, scheme.primary)
                org.junit.Assert.assertTrue("primary sur $surface : $ratio", ratio >= 4.5)
            }
        }
    }
}
