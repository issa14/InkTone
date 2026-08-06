package com.inktone.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.inktone.domain.model.PublicationFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Lot 2a.7 — popup de filtrage (UX §Popup de filtrage). Garde-fou du
 * critère 2 : chaque section doit réellement émettre son intent, pas
 * juste changer d'apparence.
 */
class LibraryFilterDialogTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectionner_un_tri_emet_le_bon_ordre() {
        var received: LibrarySortOrder? = null
        composeTestRule.setContent {
            MaterialTheme {
                LibraryFilterDialog(
                    sortOrder = LibrarySortOrder.RECENTLY_ADDED,
                    onSortOrderChange = { received = it },
                    selectedFormats = emptySet(),
                    onToggleFormat = {},
                    onClearFormats = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Titre").performClick()

        assertEquals(LibrarySortOrder.TITLE, received)
    }

    @Test
    fun changer_la_disposition_emet_le_bon_mode() {
        var received: LibraryLayoutMode? = null
        composeTestRule.setContent {
            MaterialTheme {
                LibraryFilterDialog(
                    sortOrder = LibrarySortOrder.RECENTLY_ADDED,
                    onSortOrderChange = {},
                    layoutMode = LibraryLayoutMode.GRID_COVERS,
                    onLayoutModeChange = { received = it },
                    selectedFormats = emptySet(),
                    onToggleFormat = {},
                    onClearFormats = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Liste").performClick()

        assertEquals(LibraryLayoutMode.LIST, received)
    }

    @Test
    fun cocher_epub_puis_txt_produit_une_selection_multiple() {
        var formats by mutableStateOf(emptySet<PublicationFormat>())
        composeTestRule.setContent {
            MaterialTheme {
                LibraryFilterDialog(
                    sortOrder = LibrarySortOrder.RECENTLY_ADDED,
                    onSortOrderChange = {},
                    selectedFormats = formats,
                    onToggleFormat = { formats = if (it in formats) formats - it else formats + it },
                    onClearFormats = { formats = emptySet() },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("EPUB").performClick()
        composeTestRule.onNodeWithText("TXT").performClick()

        assertTrue(PublicationFormat.EPUB in formats)
        assertTrue(PublicationFormat.TXT in formats)
        assertEquals(2, formats.size)
    }

    @Test
    fun statut_masque_ne_s_affiche_pas_pour_l_ecran_de_detail() {
        composeTestRule.setContent {
            MaterialTheme {
                LibraryFilterDialog(
                    sortOrder = LibrarySortOrder.RECENTLY_ADDED,
                    onSortOrderChange = {},
                    selectedFormats = emptySet(),
                    onToggleFormat = {},
                    onClearFormats = {},
                    onDismiss = {},
                    showStatusFilter = false,
                    showLayoutSection = false,
                )
            }
        }

        composeTestRule.onNodeWithText("Filtrer par").assertDoesNotExist()
        composeTestRule.onNodeWithText("Mise en page").assertDoesNotExist()
    }
}
