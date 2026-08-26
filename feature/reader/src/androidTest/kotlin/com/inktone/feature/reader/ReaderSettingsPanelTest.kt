package com.inktone.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 3d.6, tests 5 et 6 — panneau TT reconstruit (3d.2) : plus de cartes de
 * thème (redondantes depuis la bascule cyclique du lot 3b), curseurs
 * continus (pas de paliers), aperçu du texte réellement en cours de
 * lecture.
 */
class ReaderSettingsPanelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun plus_de_cartes_de_theme_dans_le_panneau_tt() {
        composeTestRule.setContent {
            ReaderSettingsPanel(
                currentFontSize = 18,
                currentLineHeightMultiplier = 1.4f,
                previewText = "Extrait du chapitre en cours de lecture.",
                previewTextColor = Color.Black,
                previewBackgroundColor = Color.White,
                onFontSizeChange = {},
                onLineHeightChange = {},
                currentMarginStep = 1,
            isTextJustified = false,
            keepScreenOn = false,
            autoScrollSpeed = 0,
            reduceMotion = false,
            isScrollMode = true,
            onMarginStepChange = {},
            onTextJustifiedChange = {},
            onKeepScreenOnChange = {},
            onAutoScrollSpeedChange = {},
            onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Clair").assertDoesNotExist()
        composeTestRule.onNodeWithText("Sombre").assertDoesNotExist()
        composeTestRule.onNodeWithText("Sépia").assertDoesNotExist()
    }

    @Test
    fun l_apercu_affiche_le_texte_reellement_en_cours_de_lecture() {
        composeTestRule.setContent {
            ReaderSettingsPanel(
                currentFontSize = 18,
                currentLineHeightMultiplier = 1.4f,
                previewText = "Il était une fois, dans un royaume lointain.",
                previewTextColor = Color.Black,
                previewBackgroundColor = Color.White,
                onFontSizeChange = {},
                onLineHeightChange = {},
                currentMarginStep = 1,
            isTextJustified = false,
            keepScreenOn = false,
            autoScrollSpeed = 0,
            reduceMotion = false,
            isScrollMode = true,
            onMarginStepChange = {},
            onTextJustifiedChange = {},
            onKeepScreenOnChange = {},
            onAutoScrollSpeedChange = {},
            onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Il était une fois, dans un royaume lointain.").assertIsDisplayed()
    }

    @Test
    fun les_deux_curseurs_sont_continus_sans_paliers() {
        composeTestRule.setContent {
            ReaderSettingsPanel(
                currentFontSize = 18,
                currentLineHeightMultiplier = 1.4f,
                previewText = "Aperçu.",
                previewTextColor = Color.Black,
                previewBackgroundColor = Color.White,
                onFontSizeChange = {},
                onLineHeightChange = {},
                currentMarginStep = 1,
            isTextJustified = false,
            keepScreenOn = false,
            autoScrollSpeed = 0,
            reduceMotion = false,
            isScrollMode = true,
            onMarginStepChange = {},
            onTextJustifiedChange = {},
            onKeepScreenOnChange = {},
            onAutoScrollSpeedChange = {},
            onDismiss = {},
            )
        }

        // Vérifie directement via le SemanticsProperties.ProgressBarRangeInfo
        // (steps) plutôt que de deviner un texte : steps = 0 signifie
        // "aucun palier", exactement ce que 3d.2 exige (avant : steps = 19).
        val nodes = composeTestRule.onAllNodes(SemanticsMatcher("has ProgressBarRangeInfo") { node ->
            node.config.contains(SemanticsProperties.ProgressBarRangeInfo)
        })
        nodes.fetchSemanticsNodes().forEach { node ->
            val rangeInfo = node.config[SemanticsProperties.ProgressBarRangeInfo]
            assertEquals("curseur continu attendu (steps=0)", 0, rangeInfo.steps)
        }
    }

    // ───── Lot 21, tâche 9 — auto-scroll visuel ─────

    @Test
    fun le_panneau_propose_les_crans_de_vitesse_d_auto_scroll() {
        composeTestRule.setContent {
            ReaderSettingsPanel(
                currentFontSize = 18,
                currentLineHeightMultiplier = 1.4f,
                previewText = "Aperçu.",
                previewTextColor = Color.Black,
                previewBackgroundColor = Color.White,
                onFontSizeChange = {},
                onLineHeightChange = {},
                currentMarginStep = 1,
                isTextJustified = false,
                keepScreenOn = false,
                autoScrollSpeed = 2,
                reduceMotion = false,
                isScrollMode = true,
                onMarginStepChange = {},
                onTextJustifiedChange = {},
                onKeepScreenOnChange = {},
                onAutoScrollSpeedChange = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Auto-scroll").assertExists()
        composeTestRule.onNodeWithText("Désactivé").assertExists()
        composeTestRule.onNodeWithText("Lente").assertExists()
        composeTestRule.onNodeWithText("Moyenne").assertExists()
        composeTestRule.onNodeWithText("Rapide").assertExists()
    }
}
