package com.inktone.feature.reader

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Tâche 3e.4, palier A — les 5 contrôles de la barre pilule émettent
 * chacun leur intent (aucun callback vide), et les contrôles de chapitre
 * sont désactivés aux extrémités du livre plutôt que masqués.
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
                hasPreviousChapter = true,
                hasNextChapter = true,
                onPreviousChapter = { clicked += "ChapitrePrecedent" },
                onPreviousSentence = { clicked += "PhrasePrecedente" },
                onPlayPause = { clicked += "PlayPause" },
                onNextSentence = { clicked += "PhraseSuivante" },
                onNextChapter = { clicked += "ChapitreSuivant" },
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
                hasPreviousChapter = true,
                hasNextChapter = true,
                onPreviousChapter = {},
                onPreviousSentence = {},
                onPlayPause = {},
                onNextSentence = {},
                onNextChapter = {},
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
                hasPreviousChapter = false,
                hasNextChapter = false,
                onPreviousChapter = { clicked += "ChapitrePrecedent" },
                onPreviousSentence = {},
                onPlayPause = {},
                onNextSentence = {},
                onNextChapter = { clicked += "ChapitreSuivant" },
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
    fun le_bouton_replie_emet_l_intent_de_redeploiement() {
        var expanded = false

        composeTestRule.setContent {
            TtsPillBarCollapsed(onExpand = { expanded = true })
        }

        composeTestRule.onNodeWithContentDescription("Afficher les contrôles de lecture").performClick()
        assertEquals(true, expanded)
    }
}
