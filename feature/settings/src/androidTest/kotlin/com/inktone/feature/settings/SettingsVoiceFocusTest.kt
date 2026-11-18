package com.inktone.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.inktone.domain.model.UserPreferences
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Audit UX A7 : la proposition du Lecteur ouvrait les Réglages en haut de page,
 * la voix neuronale en bas d'écran. Avec `focusVoiceDownload`, la page y défile.
 */
class SettingsVoiceFocusTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val voiceRowText = "Voix naturelle (Jessica et Pierre) · 183 Mo"

    private fun voiceRowTop(focusVoiceDownload: Boolean) = run {
        composeTestRule.setContent {
            MaterialTheme {
                SettingsContent(preferences = UserPreferences(), focusVoiceDownload = focusVoiceDownload, onIntent = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(voiceRowText).getUnclippedBoundsInRoot().top
    }

    @Test
    fun arrivee_depuis_le_lecteur_remonte_la_voix_neuronale_en_haut() {
        val top = voiceRowTop(focusVoiceDownload = true)
        assertTrue("carte de la voix en haut de page, trouvée à $top", top < 200.dp)
    }

    /**
     * Témoin : sans la demande, la page n'a pas défilé — le premier titre de
     * section reste en tête. Mesurer la carte de la voix supposait une mise
     * en page figée, que le regroupement des Réglages (P1-12) a remontée.
     */
    @Test
    fun ouverture_ordinaire_laisse_la_page_en_haut() {
        voiceRowTop(focusVoiceDownload = false)
        val firstSectionTop = composeTestRule.onNodeWithText("Narration").getUnclippedBoundsInRoot().top
        // Négatif si la page avait défilé : borne basse comprise.
        assertTrue("page laissée en haut, premier titre trouvé à $firstSectionTop", firstSectionTop >= 0.dp && firstSectionTop < 48.dp)
    }
}
