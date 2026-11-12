package com.inktone.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.UserPreferences
import org.junit.Rule
import org.junit.Test

/**
 * Audit UX A16 : le curseur « Intonation » s'affichait quel que soit le
 * moteur, alors qu'aucun ne l'appliquait. Il n'apparaît désormais que pour
 * un moteur qui déclare `pitchControl` (voix système, Edge).
 */
class SettingsPitchSliderTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val pitchEngines = setOf(TtsEngineId.ANDROID_NATIVE, TtsEngineId.EDGE_TTS)

    @Test
    fun le_curseur_d_intonation_est_absent_pour_la_voix_neuronale() {
        composeTestRule.setContent {
            MaterialTheme {
                SettingsContent(
                    preferences = UserPreferences(defaultTtsEngine = TtsEngineId.SHERPA_ONNX),
                    pitchEngines = pitchEngines,
                    onIntent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Intonation").assertDoesNotExist()
        composeTestRule.onNodeWithText("Vitesse d'élocution").assertExists()
    }

    @Test
    fun le_curseur_d_intonation_est_present_pour_la_voix_systeme() {
        composeTestRule.setContent {
            MaterialTheme {
                SettingsContent(
                    preferences = UserPreferences(defaultTtsEngine = TtsEngineId.ANDROID_NATIVE),
                    pitchEngines = pitchEngines,
                    onIntent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Intonation").assertExists()
    }
}
