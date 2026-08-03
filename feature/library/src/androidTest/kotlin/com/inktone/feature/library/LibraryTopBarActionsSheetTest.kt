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
 * Lot 2a.5/2a.7 — non-régression : « Régénérer/Réinitialiser les
 * couvertures » avaient un corps de méthode vide côté ViewModel
 * (contrôles décoratifs, critère 2). Retirées du bottom sheet 3-points ;
 * ce test échoue si elles réapparaissent.
 */
class LibraryTopBarActionsSheetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun le_menu_3_points_ne_montre_plus_les_actions_de_couverture() {
        composeTestRule.setContent {
            MaterialTheme {
                LibraryTopBar(
                    onMenuClick = {},
                    searchQuery = "",
                    onSearchQueryChange = {},
                    sortOrder = LibrarySortOrder.RECENTLY_ADDED,
                    onSortOrderChange = {},
                    layoutMode = LibraryLayoutMode.GRID_COVERS,
                    onLayoutModeChange = {},
                    selectedFormats = emptySet<PublicationFormat>(),
                    onToggleFormat = {},
                    onClearFormats = {},
                    onRefresh = {},
                    onImportClick = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Actions").performClick()

        composeTestRule.onNodeWithText("Importer").assertExists()
        composeTestRule.onNodeWithText("Actualiser").assertExists()
        composeTestRule.onNodeWithText("Régénérer les couvertures").assertDoesNotExist()
        composeTestRule.onNodeWithText("Réinitialiser les couvertures").assertDoesNotExist()
    }
}
