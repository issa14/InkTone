package com.inktone.feature.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.AnnotationKind
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
                onHighlight = { _, _ -> },
                onSaveNote = { _, _, _ -> },
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
                onHighlight = { _, _ -> },
                onSaveNote = { _, _, _ -> },
                onDismiss = { dismissed = true },
            )
        }

        composeTestRule.onNodeWithText("Copier").performClick()
        assertTrue(dismissed)
    }

    /**
     * Lot 24, décision 1 — remplace l'ancien
     * `surligner_ouvre_le_choix_de_couleur_puis_confirme` : il n'y a plus
     * de bouton de confirmation, taper la pastille par défaut (déjà
     * sélectionnée) applique directement.
     */
    @Test
    fun taper_la_pastille_par_defaut_applique_directement_sans_confirmation() {
        var highlightedColor: AnnotationColor? = null
        composeTestRule.setContent {
            SelectionActionPopup(
                selectedText = "Un passage sélectionné.",
                selectionBoundsInWindow = someBounds,
                onHighlight = { color, _ -> highlightedColor = color },
                onSaveNote = { _, _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Surligner").performClick()
        composeTestRule.onNodeWithContentDescription("Couleur Jaune").performClick()

        assertEquals(AnnotationColor.YELLOW, highlightedColor) // couleur par défaut
    }

    /**
     * Lot 24, décision 1 — le popup ne se ferme pas après une application :
     * un second tap sur une autre pastille réapplique (change) la couleur,
     * sans qu'il faille rouvrir « Surligner ».
     */
    @Test
    fun retaper_une_autre_pastille_reapplique_sans_rouvrir_surligner() {
        val highlightedColors = mutableListOf<AnnotationColor>()
        composeTestRule.setContent {
            SelectionActionPopup(
                selectedText = "Un passage sélectionné.",
                selectionBoundsInWindow = someBounds,
                onHighlight = { color, _ -> highlightedColors += color },
                onSaveNote = { _, _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Surligner").performClick()
        composeTestRule.onNodeWithContentDescription("Couleur Jaune").performClick()
        composeTestRule.onNodeWithContentDescription("Couleur Vert").performClick()

        assertEquals(listOf(AnnotationColor.YELLOW, AnnotationColor.GREEN), highlightedColors)
    }

    /** Lot 23, tâche 8 — pastille pleine (plus de `FilterChip` texte « Vert »). */
    @Test
    fun choisir_une_pastille_de_couleur_transmet_cette_couleur() {
        var highlightedColor: AnnotationColor? = null
        composeTestRule.setContent {
            SelectionActionPopup(
                selectedText = "Un passage sélectionné.",
                selectionBoundsInWindow = someBounds,
                onHighlight = { color, _ -> highlightedColor = color },
                onSaveNote = { _, _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Surligner").performClick()
        composeTestRule.onNodeWithContentDescription("Couleur Vert").performClick()

        assertEquals(AnnotationColor.GREEN, highlightedColor)
    }

    /**
     * Lot 23, tâche 9 — la couleur personnalisée validée devient la couleur
     * du surlignage. Lot 24, décision 3 — « Appliquer » du dialogue RGB
     * applique directement, aucun second tap sur « Surligner » requis.
     */
    @Test
    fun personnaliser_une_couleur_puis_confirmer_transmet_cette_couleur() {
        var highlightedColor: AnnotationColor? = null
        composeTestRule.setContent {
            SelectionActionPopup(
                selectedText = "Un passage sélectionné.",
                selectionBoundsInWindow = someBounds,
                onHighlight = { color, _ -> highlightedColor = color },
                onSaveNote = { _, _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Surligner").performClick()
        composeTestRule.onNodeWithContentDescription("Personnaliser la couleur").performClick()
        composeTestRule.onNodeWithText("Ou saisir un code hexadécimal").performTextInput("#123456")
        composeTestRule.onNodeWithText("Appliquer").performClick()

        assertEquals(hexRgbToAnnotationColor("#123456"), highlightedColor)
    }

    /**
     * Lot 23, tâche 6 — le trou trouvé à la vérification device du Lot 22 :
     * aucune action ne permettait de choisir souligné/barré. `AnnotationKind`
     * par défaut reste `HIGHLIGHT` tant que l'utilisateur ne choisit pas
     * explicitement autre chose (aucun changement de comportement). Lot 24,
     * décision 1 — choisir un type applique directement, aucun second tap
     * sur « Surligner » requis.
     */
    @Test
    fun choisir_souligne_transmet_AnnotationKind_UNDERLINE() {
        var highlightedKind: AnnotationKind? = null
        composeTestRule.setContent {
            SelectionActionPopup(
                selectedText = "Un passage sélectionné.",
                selectionBoundsInWindow = someBounds,
                onHighlight = { _, kind -> highlightedKind = kind },
                onSaveNote = { _, _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Surligner").performClick()
        composeTestRule.onNodeWithText("Souligné").performClick()

        assertEquals(AnnotationKind.UNDERLINE, highlightedKind)
    }

    @Test
    fun note_saisie_puis_enregistree_transmet_le_texte() {
        var savedContent: String? = null
        composeTestRule.setContent {
            SelectionActionPopup(
                selectedText = "Un passage sélectionné.",
                selectionBoundsInWindow = someBounds,
                onHighlight = { _, _ -> },
                onSaveNote = { content, _, _ -> savedContent = content },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Note").performClick()
        composeTestRule.onNodeWithText("Note").performTextInput("Ma note de lecture")
        composeTestRule.onNodeWithText("Enregistrer").performClick()

        assertEquals("Ma note de lecture", savedContent)
    }

    /** Audit UX D52 : la couleur d'une note se choisit sans créer de surlignage. */
    @Test
    fun la_couleur_choisie_en_mode_note_va_a_la_note_sans_rien_surligner() {
        var highlighted = false
        var savedColor: AnnotationColor? = null
        composeTestRule.setContent {
            SelectionActionPopup(
                selectedText = "Un passage sélectionné.",
                selectionBoundsInWindow = someBounds,
                onHighlight = { _, _ -> highlighted = true },
                onSaveNote = { _, color, _ -> savedColor = color },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Note").performClick()
        composeTestRule.onNodeWithContentDescription("Couleur Bleu").performClick()
        composeTestRule.onNodeWithText("Note").performTextInput("Ma note de lecture")
        composeTestRule.onNodeWithText("Enregistrer").performClick()

        assertEquals(false, highlighted)
        assertEquals(AnnotationColor.BLUE, savedColor)
    }

    /** Audit UX D50 (partie page) : l'action n'apparaît que lorsqu'une extension est possible. */
    @Test
    fun etendre_la_selection_n_apparait_que_si_proposee_et_la_declenche() {
        var extended = false
        var onExtend: (() -> Unit)? by mutableStateOf(null)
        composeTestRule.setContent {
            SelectionActionPopup(
                selectedText = "mot",
                selectionBoundsInWindow = someBounds,
                onHighlight = { _, _ -> },
                onSaveNote = { _, _, _ -> },
                onDismiss = {},
                onExtend = onExtend,
            )
        }
        composeTestRule.onNodeWithText("Étendre la sélection jusqu'ici").assertDoesNotExist()

        onExtend = { extended = true }
        composeTestRule.onNodeWithText("Étendre la sélection jusqu'ici").performClick()

        assertTrue(extended)
    }

    @Test
    fun aucune_borne_de_selection_n_affiche_pas_de_popup() {
        composeTestRule.setContent {
            SelectionActionPopup(
                selectedText = "",
                selectionBoundsInWindow = null,
                onHighlight = { _, _ -> },
                onSaveNote = { _, _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Copier").assertDoesNotExist()
    }
}
