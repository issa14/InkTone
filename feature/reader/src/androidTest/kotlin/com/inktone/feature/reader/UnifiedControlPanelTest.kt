package com.inktone.feature.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Tâche 3b.7, test 4 — les 9 icônes des rangées 2 et 3 (+ Play) émettent
 * chacune leur intent, aucune n'a de callback vide. La Luminosité est rangée
 * dans « Réglages du texte » (audit UI P1-6). Les boutons chapitre
 * précédent/suivant restent absents (la navigation par chapitre passe par le
 * Sommaire ou la pilule de narration).
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
            )
        }

        val expectedActions = listOf(
            "Sommaire", "Signets", "Écouter", "Thème suivant", "Réglages du texte",
            "Minuteur de sommeil", "Voix et vitesse", "Passer en mode défilement", "Rechercher dans ce livre",
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
    fun ni_navigation_chapitre_ni_luminosite_dans_le_panneau() {
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

    /** Audit UI P0-8 : TalkBack annonce les icônes du panneau comme des boutons. */
    @Test
    fun les_actions_secondaires_sont_annoncees_comme_boutons() {
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

        composeTestRule.onNodeWithContentDescription("Signets")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }
}
