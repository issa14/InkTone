package com.inktone.feature.player

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tache 9.1 — audit d'accessibilite reel sur `PlayerContent` (barre de
 * controle explicitement citee comme point d'attention par le plan de
 * Phase 9, Tache 9.1.1).
 */
class PlayerAccessibilityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tous_les_controles_du_player_ont_une_description_accessible() {
        composeTestRule.setContent {
            MaterialTheme {
                PlayerContent(state = PlayerUiState(isConnected = true), onIntent = {})
            }
        }

        composeTestRule.onAllNodes(hasClickAction())
            .assertAll(
                SemanticsMatcher("a une description accessible") { node ->
                    node.config.contains(SemanticsProperties.ContentDescription) ||
                        node.config.contains(SemanticsProperties.Text)
                },
            )
    }

    @Test
    fun toutes_les_cibles_tactiles_du_player_font_au_moins_48dp() {
        composeTestRule.setContent {
            MaterialTheme {
                PlayerContent(state = PlayerUiState(isConnected = true), onIntent = {})
            }
        }

        composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            val heightDp = with(composeTestRule.density) { node.boundsInRoot.height.toDp() }
            assertTrue("cible sous 48dp trouvee : $heightDp", heightDp >= 48.dp)
        }
    }
}
