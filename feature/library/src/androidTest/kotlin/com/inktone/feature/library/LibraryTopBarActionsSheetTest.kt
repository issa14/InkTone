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
 * Lot 2a.5/2a.7 puis Lot 19 — non-régression : « Régénérer/Réinitialiser
 * les couvertures » avaient un corps de méthode vide côté ViewModel
 * (contrôles décoratifs, critère 2). Le Lot 19 réintroduit les cinq
 * actions de la cible UX avec leur logique réelle, sous les libellés
 * « Couverture par défaut » et « Reconstruire les couvertures » — ce
 * test échoue si les anciens libellés réapparaissent ou si une action
 * de la cible manque.
 */
class LibraryTopBarActionsSheetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun le_menu_3_points_expose_les_cinq_actions_de_la_cible() {
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
                    onImportClick = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Actions").performClick()

        composeTestRule.onNodeWithText("Importer").assertExists()
        composeTestRule.onNodeWithText("Couverture par défaut").assertExists()
        composeTestRule.onNodeWithText("Reconstruire les couvertures").assertExists()
        composeTestRule.onNodeWithText("Ouvrir un livre au hasard").assertExists()
        composeTestRule.onNodeWithText("Synchroniser avec le cloud").assertExists()

        // Libellés legacy — jamais réintroduits.
        composeTestRule.onNodeWithText("Régénérer les couvertures").assertDoesNotExist()
        composeTestRule.onNodeWithText("Réinitialiser les couvertures").assertDoesNotExist()
        // « Actualiser » n'est pas dans les cinq actions de la cible.
        composeTestRule.onNodeWithText("Actualiser").assertDoesNotExist()
    }
}
