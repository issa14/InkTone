package com.inktone.feature.onboarding

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * Lot 10, Tâche 10.7, point 5 — « Les illustrations ne contiennent
 * aucune couleur littérale ; changer de thème change leur rendu. »
 * Vérifié pixel à pixel (pas seulement par lecture du code) : le premier
 * bâtonnet de [AudioIconIllustration] est un rectangle PLEIN (pas un
 * trait) rempli avec `neutralColor` — coordonnée connue, échantillonnage
 * fiable, contrairement aux traits de [BookIconIllustration] dont
 * l'épaisseur rend la coïncidence pixel-exacte incertaine.
 */
class OnboardingIllustrationsColorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun changer_neutralColor_change_reellement_le_pixel_rendu() {
        val first = Color(0xFFFF0000)
        val second = Color(0xFF0000FF)

        composeTestRule.setContent {
            AudioIconIllustration(modifier = Modifier.size(100.dp), neutralColor = first, accentColor = Color.Black)
        }
        val pixelWithFirstColor = composeTestRule.onRoot().captureToImage().let { image ->
            image.asAndroidBitmap().getPixel((image.width * 0.2f).toInt(), (image.height * 0.5f).toInt())
        }

        composeTestRule.setContent {
            AudioIconIllustration(modifier = Modifier.size(100.dp), neutralColor = second, accentColor = Color.Black)
        }
        val pixelWithSecondColor = composeTestRule.onRoot().captureToImage().let { image ->
            image.asAndroidBitmap().getPixel((image.width * 0.2f).toInt(), (image.height * 0.5f).toInt())
        }

        assertEquals(first.toArgb(), pixelWithFirstColor)
        assertEquals(second.toArgb(), pixelWithSecondColor)
        assertNotEquals(pixelWithFirstColor, pixelWithSecondColor)
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)
