package com.inktone.feature.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Tâche 3b.7, test 4 — les 9 icônes des rangées 2 et 3 (+ Play) émettent
 * chacune leur intent, aucune n'a de callback vide. Non-régression :
 * pas d'icône Luminosité, pas de boutons chapitre précédent/suivant
 * (retirés en 3b.6, la navigation par chapitre passe désormais
 * uniquement par le Sommaire).
 */
class UnifiedControlPanelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun toutes_les_actions_du_panneau_emettent_leur_intent() {
        val clicked = mutableSetOf<String>()

        composeTestRule.setContent {
            UnifiedControlPanel(
                isPlaying = false,
                sleepTimerActive = false,
                bookProgression = 0.5f,
                onPlayPause = { clicked += "PlayPause" },
                onSleepTimerClick = { clicked += "Minuteur" },
                onSearchClick = { clicked += "Recherche" },
                onBookmarksClick = { clicked += "Marque-pages" },
                onTocClick = { clicked += "Sommaire" },
                onThemeCycle = { clicked += "Thème" },
                onAaClick = { clicked += "TT" },
                onTtsClick = { clicked += "Haut-parleur" },
                onReadingModeClick = { clicked += "Mode" },
            )
        }

        val expectedActions = listOf(
            "Sommaire", "Marque-pages", "Lire", "Thème", "TT",
            "Minuteur", "Haut-parleur", "Mode", "Recherche",
        )
        for (action in expectedActions) {
            composeTestRule.onNodeWithContentDescription(action).performClick()
        }

        val expectedIntents = setOf(
            "Sommaire", "Marque-pages", "PlayPause", "Thème", "TT",
            "Minuteur", "Haut-parleur", "Mode", "Recherche",
        )
        assertEquals(expectedIntents, clicked)
    }

    @Test
    fun pas_de_luminosite_ni_de_navigation_chapitre_dans_le_panneau() {
        composeTestRule.setContent {
            UnifiedControlPanel(
                isPlaying = false,
                sleepTimerActive = false,
                bookProgression = 0.5f,
                onPlayPause = {},
                onSleepTimerClick = {},
                onSearchClick = {},
                onBookmarksClick = {},
                onTocClick = {},
                onThemeCycle = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Luminosité").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Chapitre precedent").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Chapitre suivant").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Pas de chapitre precedent").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Pas de chapitre suivant").assertDoesNotExist()
    }
}
