package com.inktone.feature.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Tâche 3b.7, test 4, révisé en 3d.3/3d.6 (test 8 du lot 3d) — les 10
 * icônes des rangées 2 et 3 (+ Play) émettent chacune leur intent, aucune
 * n'a de callback vide. Non-régression inverse de celle posée en 3b.7 :
 * l'icône Luminosité DOIT désormais exister (rangée 3 à 5 icônes,
 * ajoutée avec son action en 3d.3, jamais avant). Les boutons chapitre
 * précédent/suivant restent absents (retirés en 3b.6, la navigation par
 * chapitre passe uniquement par le Sommaire).
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
                readingMode = ReadingMode.PAGED,
                onPlayPause = { clicked += "PlayPause" },
                onSleepTimerClick = { clicked += "Minuteur" },
                onSearchClick = { clicked += "Recherche" },
                onBookmarksClick = { clicked += "Marque-pages" },
                onTocClick = { clicked += "Sommaire" },
                onThemeCycle = { clicked += "Thème" },
                onAaClick = { clicked += "TT" },
                onTtsClick = { clicked += "Haut-parleur" },
                onReadingModeClick = { clicked += "Mode" },
                onBrightnessClick = { clicked += "Luminosité" },
            )
        }

        val expectedActions = listOf(
            "Sommaire", "Marque-pages", "Lire", "Thème", "Réglages du texte",
            "Minuteur", "Haut-parleur", "Mode pages", "Recherche", "Luminosité",
        )
        for (action in expectedActions) {
            composeTestRule.onNodeWithContentDescription(action).performClick()
        }

        val expectedIntents = setOf(
            "Sommaire", "Marque-pages", "PlayPause", "Thème", "TT",
            "Minuteur", "Haut-parleur", "Mode", "Recherche", "Luminosité",
        )
        assertEquals(expectedIntents, clicked)
    }

    @Test
    fun pas_de_navigation_chapitre_dans_le_panneau_mais_luminosite_presente() {
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

        composeTestRule.onNodeWithContentDescription("Luminosité").assertExists()
        composeTestRule.onNodeWithContentDescription("Chapitre precedent").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Chapitre suivant").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Pas de chapitre precedent").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Pas de chapitre suivant").assertDoesNotExist()
    }
}
