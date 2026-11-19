package com.inktone.core.designsystem

import androidx.compose.material3.ColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

/** Séries des graphiques : lisibles sur les surfaces du thème et distinctes une fois empilées. */
class ChartColorsContrastTest {

    private val cases: List<Triple<String, InkToneChartColors, ColorScheme>> = listOf(
        Triple("clair", LightChartColors, InkToneLightColorScheme),
        Triple("sombre", DarkChartColors, InkToneDarkColorScheme),
    )

    @Test
    fun chaque_serie_passe_3_pour_1_sur_les_surfaces() {
        cases.forEach { (name, colors, s) ->
            listOf(s.background, s.surface, s.surfaceContainerHigh).forEach { surface ->
                listOf("visual" to colors.visual, "tts" to colors.tts).forEach { (serie, color) ->
                    val ratio = calculateContrastRatio(surface, color)
                    assertTrue("$name $serie : $ratio < 3.0", ratio >= 3.0)
                }
            }
        }
    }

    @Test
    fun les_deux_series_se_distinguent_l_une_de_l_autre() {
        cases.forEach { (name, colors, _) ->
            val ratio = calculateContrastRatio(colors.visual, colors.tts)
            assertTrue("$name visual/tts : $ratio < 2.0", ratio >= 2.0)
        }
    }
}
