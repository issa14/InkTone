package com.inktone.feature.reader

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Tâche 3e.4 — les 5 contrôles de la barre pilule (palier A) émettent
 * chacun leur intent (aucun callback vide), les contrôles de chapitre
 * sont désactivés aux extrémités du livre plutôt que masqués (palier A),
 * le repli en bouton émet son intent de redéploiement (palier B), et le
 * balayage vers le bas émet exactement l'intent décidé — une pause, pas
 * un arrêt réel (palier C, voir KDoc de TtsPillBarCollapsed).
 */
class TtsPillBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun les_cinq_controles_emettent_leur_intent() {
        val clicked = mutableSetOf<String>()

        composeTestRule.setContent {
            TtsPillBar(
                isPlaying = false,
                isAudioActive = false,
                reduceMotion = false,
                hasPreviousChapter = true,
                hasNextChapter = true,
                onPreviousChapter = { clicked += "ChapitrePrecedent" },
                onPreviousSentence = { clicked += "PhrasePrecedente" },
                onPlayPause = { clicked += "PlayPause" },
                onNextSentence = { clicked += "PhraseSuivante" },
                onNextChapter = { clicked += "ChapitreSuivant" },
                onSwipeDown = { clicked += "SwipeDown" },
            )
        }

        val expectedActions = listOf(
            "Chapitre précédent", "Phrase précédente", "Lire", "Phrase suivante", "Chapitre suivant",
        )
        for (action in expectedActions) {
            composeTestRule.onNodeWithContentDescription(action).performClick()
        }

        val expectedIntents = setOf(
            "ChapitrePrecedent", "PhrasePrecedente", "PlayPause", "PhraseSuivante", "ChapitreSuivant",
        )
        assertEquals(expectedIntents, clicked)
    }

    @Test
    fun le_bouton_central_reflete_l_etat_de_lecture() {
        composeTestRule.setContent {
            TtsPillBar(
                isPlaying = true,
                isAudioActive = true,
                reduceMotion = false,
                hasPreviousChapter = true,
                hasNextChapter = true,
                onPreviousChapter = {},
                onPreviousSentence = {},
                onPlayPause = {},
                onNextSentence = {},
                onNextChapter = {},
                onSwipeDown = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Pause").assertExists()
        composeTestRule.onNodeWithContentDescription("Lire").assertDoesNotExist()
    }

    @Test
    fun les_controles_de_chapitre_sont_desactives_mais_presents_aux_extremites() {
        val clicked = mutableSetOf<String>()

        composeTestRule.setContent {
            TtsPillBar(
                isPlaying = false,
                isAudioActive = false,
                reduceMotion = false,
                hasPreviousChapter = false,
                hasNextChapter = false,
                onPreviousChapter = { clicked += "ChapitrePrecedent" },
                onPreviousSentence = {},
                onPlayPause = {},
                onNextSentence = {},
                onNextChapter = { clicked += "ChapitreSuivant" },
                onSwipeDown = {},
            )
        }

        // Présents (pas masqués) mais désactivés : un clic ne déclenche rien.
        composeTestRule.onNodeWithContentDescription("Chapitre précédent").assertExists().assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Chapitre suivant").assertExists().assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Chapitre précédent").performClick()
        composeTestRule.onNodeWithContentDescription("Chapitre suivant").performClick()

        assertEquals(emptySet<String>(), clicked)
    }

    @Test
    fun le_balayage_vers_le_bas_sur_la_barre_deployee_emet_un_seul_intent() {
        var swipeCount = 0

        composeTestRule.setContent {
            TtsPillBar(
                isPlaying = true,
                isAudioActive = true,
                reduceMotion = false,
                hasPreviousChapter = true,
                hasNextChapter = true,
                onPreviousChapter = {},
                onPreviousSentence = {},
                onPlayPause = {},
                onNextSentence = {},
                onNextChapter = {},
                onSwipeDown = { swipeCount++ },
            )
        }

        composeTestRule.onNodeWithTag("TtsPillBar").performTouchInput { swipeDown() }
        assertEquals(1, swipeCount)
    }

    @Test
    fun le_bouton_replie_emet_l_intent_de_redeploiement() {
        var expanded = false

        composeTestRule.setContent {
            TtsPillBarCollapsed(
                isAudioActive = false,
                reduceMotion = false,
                onExpand = { expanded = true },
                onSwipeDown = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Afficher les contrôles de lecture").performClick()
        assertEquals(true, expanded)
    }

    @Test
    fun le_balayage_vers_le_bas_sur_le_bouton_replie_emet_une_pause_pas_un_expand() {
        var swipeCount = 0
        var expanded = false

        composeTestRule.setContent {
            TtsPillBarCollapsed(
                isAudioActive = true,
                reduceMotion = false,
                onExpand = { expanded = true },
                onSwipeDown = { swipeCount++ },
            )
        }

        composeTestRule.onNodeWithTag("TtsPillBarCollapsed").performTouchInput { swipeDown() }
        assertEquals(1, swipeCount)
        assertEquals(false, expanded)
    }
}
