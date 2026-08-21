package com.inktone.feature.player

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.inktone.domain.service.PlaybackSessionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Comportement du mini-lecteur (P2). Il remplace `PlayerAccessibilityTest`,
 * qui testait un écran jamais atteignable (`PlayerScreen` n'était référencé
 * par aucune route).
 */
class MiniPlayerContentTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val playing = MiniPlayerUiState(
        sessionState = PlaybackSessionState.PLAYING,
        isPlaying = true,
        title = "Les Misérables",
        author = "Victor Hugo",
        publicationId = "pub-1",
    )

    @Test
    fun affiche_le_livre_narre_et_ses_commandes_pendant_la_lecture() {
        composeTestRule.setContent {
            MaterialTheme { MiniPlayerContent(state = playing, onIntent = {}, onOpenReader = {}) }
        }

        composeTestRule.onNodeWithText("Les Misérables").assertIsDisplayed()
        composeTestRule.onNodeWithText("Victor Hugo").assertIsDisplayed()
        // Toutes les commandes portent une description accessible.
        composeTestRule.onNodeWithContentDescription("Phrase précédente").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Mettre en pause").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Phrase suivante").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Arrêter la narration").assertIsDisplayed()
    }

    @Test
    fun reste_affiche_en_pause_avec_le_bouton_de_reprise() {
        // La pause est l'état où la barre est la plus utile : la masquer
        // priverait du seul moyen de reprendre sans rouvrir le Lecteur.
        composeTestRule.setContent {
            MaterialTheme {
                MiniPlayerContent(
                    state = playing.copy(sessionState = PlaybackSessionState.PAUSED, isPlaying = false),
                    onIntent = {},
                    onOpenReader = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Reprendre la lecture").assertIsDisplayed()
    }

    @Test
    fun ramene_au_livre_reellement_narre_et_non_au_dernier_ouvert() {
        var opened: String? = null
        composeTestRule.setContent {
            MaterialTheme {
                MiniPlayerContent(state = playing, onIntent = {}, onOpenReader = { opened = it })
            }
        }

        composeTestRule.onNodeWithText("Les Misérables").performClick()
        assertEquals("pub-1", opened)
    }

    @Test
    fun emet_les_commandes_de_session_attendues() {
        val intents = mutableListOf<MiniPlayerIntent>()
        composeTestRule.setContent {
            MaterialTheme {
                MiniPlayerContent(state = playing, onIntent = { intents += it }, onOpenReader = {})
            }
        }

        composeTestRule.onNodeWithContentDescription("Phrase précédente").performClick()
        composeTestRule.onNodeWithContentDescription("Mettre en pause").performClick()
        composeTestRule.onNodeWithContentDescription("Phrase suivante").performClick()
        composeTestRule.onNodeWithContentDescription("Arrêter la narration").performClick()

        assertEquals(
            listOf(
                MiniPlayerIntent.PreviousSentence,
                MiniPlayerIntent.PlayPause,
                MiniPlayerIntent.NextSentence,
                MiniPlayerIntent.Stop,
            ),
            intents,
        )
    }
}
