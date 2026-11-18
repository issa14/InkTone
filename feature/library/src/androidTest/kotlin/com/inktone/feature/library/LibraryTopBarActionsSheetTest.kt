package com.inktone.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.inktone.domain.model.PublicationFormat
import org.junit.Rule
import org.junit.Test

/**
 * Non-régression du menu 3-points : les actions de la cible UX y sont, et la
 * maintenance des couvertures n'y revient pas — elle vit dans Réglages ›
 * Données depuis l'audit UI P1-17.
 */
class LibraryTopBarActionsSheetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun le_menu_3_points_expose_les_actions_de_la_cible() {
        composeTestRule.setContent {
            MaterialTheme {
                LibraryTopBar(
                    onMenuClick = {},
                    searchQuery = "",
                    onSearchQueryChange = {},
                    isSearchActive = false,
                    onSearchActiveChange = {},
                    sortOrder = LibrarySortOrder.RECENTLY_ADDED,
                    onSortOrderChange = {},
                    layoutMode = LibraryLayoutMode.GRID_COVERS,
                    onLayoutModeChange = {},
                    selectedFormats = emptySet<PublicationFormat>(),
                    onToggleFormat = {},
                    onClearFormats = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Actions").performClick()

        // Importer est le bouton flottant de l'écran (audit UI P1-11).
        composeTestRule.onNodeWithText("Importer").assertDoesNotExist()
        composeTestRule.onNodeWithText("Rechercher dans les livres").assertExists()
        composeTestRule.onNodeWithText("Ouvrir un livre au hasard").assertExists()
        composeTestRule.onNodeWithText("Synchroniser avec le cloud").assertExists()

        // Maintenance des couvertures : dans les Réglages, plus ici.
        composeTestRule.onNodeWithText("Couverture par défaut").assertDoesNotExist()
        composeTestRule.onNodeWithText("Reconstruire les couvertures").assertDoesNotExist()
        composeTestRule.onNodeWithText("Régénérer les couvertures").assertDoesNotExist()
        // « Actualiser » n'est pas dans les actions de la cible.
        composeTestRule.onNodeWithText("Actualiser").assertDoesNotExist()
    }
}
