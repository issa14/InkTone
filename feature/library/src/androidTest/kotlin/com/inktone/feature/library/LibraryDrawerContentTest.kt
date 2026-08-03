package com.inktone.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Lot 1 — garde-fou du critère « zéro décoration » : un item du drawer
 * qui n'appelle rien fait échouer ce test. `LibraryDrawerContent` est
 * sans état (pattern `SettingsContent`, `SettingsAccessibilityTest`).
 */
class LibraryDrawerContentTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun marque_pages_et_notes_declenche_son_callback() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                LibraryDrawerContent(
                    state = LibraryUiState(),
                    onSelectFilter = { _, _ -> },
                    onOpenBookmarks = { clicked = true },
                    onOpenStats = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Marque-pages et Notes").performClick()

        assertEquals(true, clicked)
    }

    @Test
    fun statistiques_de_lecture_declenche_son_callback() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                LibraryDrawerContent(
                    state = LibraryUiState(),
                    onSelectFilter = { _, _ -> },
                    onOpenBookmarks = {},
                    onOpenStats = { clicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Statistiques de lecture").performClick()

        assertEquals(true, clicked)
    }

    @Test
    fun parametres_declenche_son_callback() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                LibraryDrawerContent(
                    state = LibraryUiState(),
                    onSelectFilter = { _, _ -> },
                    onOpenBookmarks = {},
                    onOpenStats = {},
                    onOpenSettings = { clicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Paramètres").performClick()

        assertEquals(true, clicked)
    }

    @Test
    fun a_propos_declenche_son_callback() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                LibraryDrawerContent(
                    state = LibraryUiState(),
                    onSelectFilter = { _, _ -> },
                    onOpenBookmarks = {},
                    onOpenStats = {},
                    onOpenAbout = { clicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("À propos").performClick()

        assertEquals(true, clicked)
    }

    @Test
    fun le_drawer_n_affiche_pas_recents_debug_ni_theme() {
        composeTestRule.setContent {
            MaterialTheme {
                LibraryDrawerContent(
                    state = LibraryUiState(),
                    onSelectFilter = { _, _ -> },
                    onOpenBookmarks = {},
                    onOpenStats = {},
                )
            }
        }

        composeTestRule.onAllNodesWithText("Récents").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Debug").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Thème").assertCountEquals(0)
    }

    @Test
    fun bibliotheque_est_l_item_actif_a_l_etat_initial() {
        composeTestRule.setContent {
            MaterialTheme {
                LibraryDrawerContent(
                    state = LibraryUiState(),
                    onSelectFilter = { _, _ -> },
                    onOpenBookmarks = {},
                    onOpenStats = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Bibliothèque").assertIsSelected()
        composeTestRule.onNodeWithText("Marque-pages et Notes").assertIsNotSelected()
        composeTestRule.onNodeWithText("Statistiques de lecture").assertIsNotSelected()
    }
}
