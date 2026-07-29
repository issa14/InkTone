package com.inktone.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.inktone.domain.model.UserPreferences
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tache 9.1 — audit d'accessibilite reel sur `SettingsContent` (pas une
 * checklist remplie de memoire). `SettingsContent` est la partie
 * sans-etat de `SettingsScreen` (Tache 9.1, extraite pour rester
 * testable sans Hilt/`hiltViewModel()`).
 */
class SettingsAccessibilityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tous_les_elements_cliquables_des_reglages_ont_une_description_accessible() {
        composeTestRule.setContent {
            MaterialTheme {
                SettingsContent(preferences = UserPreferences(), onIntent = {})
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
    fun toutes_les_cibles_tactiles_des_reglages_font_au_moins_48dp() {
        composeTestRule.setContent {
            MaterialTheme {
                SettingsContent(preferences = UserPreferences(), onIntent = {})
            }
        }

        composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            val heightDp = with(composeTestRule.density) { node.boundsInRoot.height.toDp() }
            assertTrue(
                "cible sous 48dp trouvee : $heightDp",
                heightDp >= 48.dp,
            )
        }
    }

    @Test
    fun le_texte_des_reglages_s_agrandit_avec_l_echelle_systeme() {
        // Une seule composition, fontScale pilote par un etat mutable -
        // setContent ne peut etre appele qu'une fois par test
        // (AndroidComposeUiTestEnvironment) ; changer l'etat declenche
        // une recomposition/remesure propre, sans monter deux arbres
        // Compose concurrents (source d'instabilite de mesure observee
        // en ecrivant ce test - deux SettingsContent montes en meme
        // temps dans une Column produisaient une hauteur mesuree a 0
        // pour le second, pas une seule fois reproductible sans cause
        // claire : evite plutot que suppose corrige).
        val fontScaleState = mutableFloatStateOf(1f)
        composeTestRule.setContent {
            val fontScale by fontScaleState
            CompositionLocalProvider(LocalDensity provides Density(density = LocalDensity.current.density, fontScale = fontScale)) {
                MaterialTheme {
                    SettingsContent(preferences = UserPreferences(), onIntent = {})
                }
            }
        }

        val nodesAtScale1 = composeTestRule.onAllNodesWithText("Taille du texte (18)", useUnmergedTree = true).fetchSemanticsNodes()
        val boundsAtScale1 = nodesAtScale1.first().boundsInRoot
        val heightAtScale1 = boundsAtScale1.bottom - boundsAtScale1.top

        composeTestRule.runOnIdle { fontScaleState.floatValue = 2f }
        composeTestRule.waitForIdle()

        val nodesAtScale2 = composeTestRule.onAllNodesWithText("Taille du texte (18)", useUnmergedTree = true).fetchSemanticsNodes()
        check(nodesAtScale2.size == nodesAtScale1.size) {
            "nombre de noeuds different apres changement d'echelle : ${nodesAtScale1.size} -> ${nodesAtScale2.size}, " +
                "bounds: ${nodesAtScale2.map { it.boundsInRoot }}"
        }
        val boundsAtScale2 = nodesAtScale2.first().boundsInRoot
        val heightAtScale2 = boundsAtScale2.bottom - boundsAtScale2.top

        // Le texte utilise bien des sp (qui suivent fontScale) et non des
        // dp fixes : a fontScale x2, la hauteur du texte doit augmenter,
        // pas rester identique (regression classique si .dp est utilise
        // par erreur sur une taille de texte, Tache 9.1.2).
        assertTrue(
            "hauteur inchangee entre fontScale=1 ($heightAtScale1) et fontScale=2 ($heightAtScale2) - .dp au lieu de .sp ?",
            heightAtScale2 > heightAtScale1,
        )
    }
}
