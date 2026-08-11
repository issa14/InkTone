package com.inktone.core.designsystem

import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Sous-lot 2b — vérifie que les 15 styles du chrome sont en Work Sans
 * et que Literata n'est jamais injectée dans la scale. Ce test fige le
 * contrat D-typo-3/D-typo-4 : tout ajout futur d'un style en police
 * système ou en Literata le fait tomber.
 */
class TypographyBrandTest {

    @Test
    fun tout_le_chrome_est_en_work_sans() {
        val styles = with(InkToneTypography) {
            listOf(
                displayLarge, displayMedium, displaySmall,
                headlineLarge, headlineMedium, headlineSmall,
                titleLarge, titleMedium, titleSmall,
                bodyLarge, bodyMedium, bodySmall,
                labelLarge, labelMedium, labelSmall,
            )
        }
        styles.forEach { style ->
            assertSame(
                "Un style du chrome n'est pas en WorkSansFamily : ${style.fontFamily}",
                WorkSansFamily,
                style.fontFamily,
            )
        }
    }

    @Test
    fun literata_n_est_pas_dans_la_scale() {
        val families = with(InkToneTypography) {
            listOf(
                displayLarge, displayMedium, displaySmall,
                headlineLarge, headlineMedium, headlineSmall,
                titleLarge, titleMedium, titleSmall,
                bodyLarge, bodyMedium, bodySmall,
                labelLarge, labelMedium, labelSmall,
            ).map { it.fontFamily }.toSet()
        }
        // NarrativeAccentFamily ne doit apparaître dans AUCUN des 15 styles
        families.forEach { family ->
            assertSame(
                "Literata (NarrativeAccentFamily) trouvée dans la scale !",
                WorkSansFamily,
                family,
            )
        }
    }
}
