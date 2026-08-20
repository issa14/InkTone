package com.inktone.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Lot 2b.5 — popup d'actions par livre (UX §Bibliothèque état peuplé).
 * Garde-fou du critère 2 : `DecorativeDots` (lot 2b.3, retiré) aurait
 * fait échouer ces tests puisqu'il n'appelait rien.
 */
class BookCoverActionsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun publication(isPinned: Boolean = false, isFavorite: Boolean = false) = Publication(
        id = "pub-1", title = "Les Misérables", authors = listOf("Victor Hugo"),
        format = PublicationFormat.EPUB, fileUri = "content://fake/1", fileHash = "hash-1",
        fileSize = 100L, chapterCount = 3, importDate = 0L, isPinned = isPinned, isFavorite = isFavorite,
    )

    @Test
    fun le_menu_3_points_ouvre_le_popup_et_chaque_action_emet_son_intent() {
        var pinned = false
        composeTestRule.setContent {
            MaterialTheme {
                BookCover(
                    publication = publication(),
                    onClick = {},
                    onToggleFavorite = {},
                    onTogglePin = { pinned = true },
                    onDelete = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Actions sur « Les Misérables »").performClick()
        composeTestRule.onNodeWithText("Épingler").assertExists()
        composeTestRule.onNodeWithText("Détails du livre").assertExists()
        composeTestRule.onNodeWithText("Retirer de la bibliothèque").assertExists()

        composeTestRule.onNodeWithText("Épingler").performClick()

        assertEquals(true, pinned)
    }

    @Test
    fun le_menu_3_points_ajoute_aux_favoris() {
        var favorited = false
        composeTestRule.setContent {
            MaterialTheme {
                BookCover(
                    publication = publication(),
                    onClick = {},
                    onToggleFavorite = { favorited = true },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Actions sur « Les Misérables »").performClick()
        composeTestRule.onNodeWithText("Ajouter aux favoris").performClick()

        assertEquals(true, favorited)
    }

    @Test
    fun le_badge_favori_est_absent_quand_non_favori() {
        composeTestRule.setContent {
            MaterialTheme {
                BookCover(publication = publication(), onClick = {}, onToggleFavorite = {})
            }
        }
        composeTestRule.onNodeWithContentDescription("Favori").assertDoesNotExist()
    }

    @Test
    fun le_badge_favori_est_visible_quand_favori() {
        composeTestRule.setContent {
            MaterialTheme {
                BookCover(publication = publication(isFavorite = true), onClick = {}, onToggleFavorite = {})
            }
        }
        composeTestRule.onNodeWithContentDescription("Favori").assertExists()
    }

    @Test
    fun epingler_affiche_detacher_quand_deja_epingle() {
        composeTestRule.setContent {
            MaterialTheme {
                BookCover(
                    publication = publication(isPinned = true),
                    onClick = {},
                    onToggleFavorite = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Actions sur « Les Misérables »").performClick()

        composeTestRule.onNodeWithText("Détacher").assertExists()
    }

    @Test
    fun details_du_livre_ouvre_la_fiche_avec_les_metadonnees() {
        composeTestRule.setContent {
            MaterialTheme {
                BookCover(publication = publication(), onClick = {}, onToggleFavorite = {})
            }
        }

        composeTestRule.onNodeWithContentDescription("Actions sur « Les Misérables »").performClick()
        composeTestRule.onNodeWithText("Détails du livre").performClick()

        composeTestRule.onNodeWithText("Victor Hugo").assertExists()
    }

    @Test
    fun refuser_la_confirmation_de_suppression_n_appelle_pas_le_use_case() {
        var deleted = false
        composeTestRule.setContent {
            MaterialTheme {
                BookCover(
                    publication = publication(),
                    onClick = {},
                    onToggleFavorite = {},
                    onDelete = { deleted = true },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Actions sur « Les Misérables »").performClick()
        composeTestRule.onNodeWithText("Retirer de la bibliothèque").performClick()
        composeTestRule.onNodeWithText("Annuler").performClick()

        assertEquals(false, deleted)
    }

    @Test
    fun accepter_la_confirmation_de_suppression_appelle_le_use_case_une_fois() {
        var deleteCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                BookCover(
                    publication = publication(),
                    onClick = {},
                    onToggleFavorite = {},
                    onDelete = { deleteCount++ },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Actions sur « Les Misérables »").performClick()
        composeTestRule.onNodeWithText("Retirer de la bibliothèque").performClick()
        composeTestRule.onNodeWithText("Retirer").performClick()

        assertEquals(1, deleteCount)
    }

    @Test
    fun le_texte_de_confirmation_mentionne_les_marque_pages_et_notes() {
        composeTestRule.setContent {
            MaterialTheme {
                DeleteConfirmationDialog(publicationTitle = "Les Misérables", onConfirm = {}, onDismiss = {})
            }
        }

        composeTestRule.onNodeWithText(
            "Cette action est irréversible. Les marque-pages et notes associés à ce livre seront également supprimés.",
        ).assertExists()
    }
}
