package com.inktone.feature.reader

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.inktone.domain.model.AnnotationColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tâche 3c.6, test 6 — les 3 actions (Copier · Surligner · Note) émettent
 * leur intent ; « Note » persiste réellement un texte (vérifié côté
 * ViewModel par `ReaderViewModelBookmarkToggleTest`, ici on vérifie que
 * le popup transmet bien le texte saisi). Signet volontairement absent
 * du popup (cible confirmée).
 */
class SelectionActionPopupTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val someBounds = Rect(left = 100f, top = 400f, right = 300f, bottom = 450f)

    @Test
    fun les_3_actions_de_premier_niveau_sont_proposees() {
        composeTestRule.setContent {
            SelectionActionPopup(
                selectedText = "Un passage sélectionné.",
                selectionBoundsInWindow = someBounds,
                onHighlight = {},
                onSaveNote = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Copier").assertExists()
        composeTestRule.onNodeWithText("Surligner").assertExists()
        composeTestRule.onNodeWithText("Note").assertExists()
        // Signet retiré (cible confirmée : une sélection n'est pas le bon
        // geste pour créer un signet, qui marque une position).
        composeTestRule.onNodeWithText("Signet").assertDoesNotExist()
    }

    @Test
    fun copier_declenche_onDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            SelectionActionPopup(
                selectedText = "Un passage sélectionné.",
                selectionBoundsInWindow = someBounds,
                onHighlight = {},
                onSaveNote = { _, _ -> },
                onDismiss = { dismissed = true },
            )
        }

        composeTestRule.onNodeWithText("Copier").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun surligner_ouvre_le_choix_de_couleur_puis_confirme() {
        var highlightedColor: AnnotationColor? = null
        composeTestRule.setContent {
            SelectionActionPopup(
                selectedText = "Un passage sélectionné.",
                selectionBoundsInWindow = someBounds,
                onHighlight = { highlightedColor = it },
                onSaveNote = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Surligner").performClick()
        composeTestRule.onNodeWithText("Surligner").performClick() // confirme dans le second temps (AnnotationColorPicker)

        assertEquals(AnnotationColor.YELLOW, highlightedColor) // couleur par défaut
    }

    @Test
    fun note_saisie_puis_enregistree_transmet_le_texte() {
        var savedContent: String? = null
        composeTestRule.setContent {
            SelectionActionPopup(
                selectedText = "Un passage sélectionné.",
                selectionBoundsInWindow = someBounds,
                onHighlight = {},
                onSaveNote = { content, _ -> savedContent = content },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Note").performClick()
        composeTestRule.onNodeWithText("Note").performTextInput("Ma note de lecture")
        composeTestRule.onNodeWithText("Enregistrer").performClick()

        assertEquals("Ma note de lecture", savedContent)
    }

    @Test
    fun aucune_borne_de_selection_n_affiche_pas_de_popup() {
        composeTestRule.setContent {
            SelectionActionPopup(
                selectedText = "",
                selectionBoundsInWindow = null,
                onHighlight = {},
                onSaveNote = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Copier").assertDoesNotExist()
    }
}
