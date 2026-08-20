package com.inktone.feature.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import org.junit.Rule
import org.junit.Test

/**
 * 3d.6, test 3 — le bouton Stop du panneau Voix n'existe plus (retiré en
 * 3d.1 : `pausePlayback()` coupe déjà entièrement l'audio, aucune
 * différence de comportement possible avec Pause tant que
 * `ReaderViewModel` n'utilise pas `AudioPlaybackService`/Media3). Le test
 * constate son absence, pas son équivalence à Pause — exactement la
 * consigne du doc du lot 3d, tâche 3d.6.
 */
class ReaderTtsPanelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val activeProfile = VoiceProfile(id = "vp-1", engine = TtsEngineId.SHERPA_ONNX, voice = "jessica", language = "fr-FR", speed = 1.6f)

    @Test
    fun aucun_bouton_arreter_dans_le_panneau_voix() {
        composeTestRule.setContent {
            ReaderTtsPanel(
                isPlaying = false,
                currentSentenceIndex = 0,
                totalSentences = 10,
                activeVoiceProfile = activeProfile,
                availableVoiceProfiles = listOf(activeProfile),
                onPlayPause = {},
                onPreviousSentence = {},
                onNextSentence = {},
                onSpeedChange = {},
                onSelectVoiceProfile = {},
                onOpenPronunciationRules = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Arrêter").assertDoesNotExist()
    }

    @Test
    fun le_curseur_de_vitesse_n_affiche_plus_la_valeur_decorative_1_0x() {
        composeTestRule.setContent {
            ReaderTtsPanel(
                isPlaying = false,
                currentSentenceIndex = 0,
                totalSentences = 10,
                activeVoiceProfile = activeProfile,
                availableVoiceProfiles = listOf(activeProfile),
                onPlayPause = {},
                onPreviousSentence = {},
                onNextSentence = {},
                onSpeedChange = {},
                onSelectVoiceProfile = {},
                onOpenPronunciationRules = {},
                onDismiss = {},
            )
        }

        // Antipattern d'origine : le curseur affichait 1.0x en dur, quel
        // que soit le profil actif. Le profil de ce test a speed=1.6f.
        composeTestRule.onNode(hasText("1.0", substring = true)).assertDoesNotExist()
        composeTestRule.onNode(hasText("Vitesse", substring = true)).assertIsDisplayed()
    }

    @Test
    fun le_nom_de_voix_affiche_suit_le_format_cible() {
        composeTestRule.setContent {
            ReaderTtsPanel(
                isPlaying = false,
                currentSentenceIndex = 0,
                totalSentences = 10,
                activeVoiceProfile = activeProfile,
                availableVoiceProfiles = listOf(activeProfile),
                onPlayPause = {},
                onPreviousSentence = {},
                onNextSentence = {},
                onSpeedChange = {},
                onSelectVoiceProfile = {},
                onOpenPronunciationRules = {},
                onDismiss = {},
            )
        }

        // Format cible UX_FLOW_DESIGN.md §Haut-parleur : "Jessica (FR) · UPMC · Français"
        composeTestRule.onNodeWithText("Jessica (FR) · UPMC · Français").assertIsDisplayed()
    }
}
