package com.inktone.core.designsystem

import androidx.compose.ui.text.TextStyle
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La hiérarchie du chrome tient à une échelle strictement décroissante dans
 * chaque famille : aplatie, un titre de ligne passait sous le texte courant
 * (IDENTITE_INKTONE.md § 3.3).
 */
class TypographyScaleTest {

    private fun assertDecreasing(family: String, vararg styles: TextStyle) {
        styles.toList().zipWithNext().forEach { (larger, smaller) ->
            assertTrue(
                "$family : ${larger.fontSize} doit dépasser ${smaller.fontSize}",
                larger.fontSize.value > smaller.fontSize.value,
            )
        }
    }

    @Test
    fun chaque_famille_decroit_strictement() = with(InkToneTypography) {
        assertDecreasing("display", displayLarge, displayMedium, displaySmall)
        assertDecreasing("headline", headlineLarge, headlineMedium, headlineSmall)
        assertDecreasing("title", titleLarge, titleMedium, titleSmall)
        assertDecreasing("body", bodyLarge, bodyMedium, bodySmall)
        assertDecreasing("label", labelLarge, labelMedium, labelSmall)
    }

    @Test
    fun le_plus_petit_grand_titre_depasse_le_titre_d_ecran() = with(InkToneTypography) {
        assertDecreasing("headline > title", headlineSmall, titleLarge)
    }

    @Test
    fun le_texte_courant_ne_depasse_pas_un_titre_de_ligne() = with(InkToneTypography) {
        assertTrue(
            "bodyMedium (${bodyMedium.fontSize}) ne doit pas dépasser titleSmall (${titleSmall.fontSize})",
            bodyMedium.fontSize.value <= titleSmall.fontSize.value,
        )
    }
}
