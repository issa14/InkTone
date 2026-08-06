package com.inktone.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Lot 2a.7 — flyout du titre (UX §Menu déroulant du titre). Distinction
 * structurante : Tous/Favoris appliquent un filtre directement, Séries
 * et Tags naviguent vers l'écran de détail — ne pas les confondre.
 */
class LibraryTitleFlyoutTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun favoris_declenche_un_changement_de_filtre_pas_une_navigation() {
        var filterApplied = false
        var navigated: String? = null
        composeTestRule.setContent {
            MaterialTheme {
                LibraryTitleFlyout(
                    expanded = true,
                    onDismiss = {},
                    series = emptyList(),
                    seriesCounts = emptyMap(),
                    tags = emptyList(),
                    tagCounts = emptyMap(),
                    onSelectAll = {},
                    onSelectFavorites = { filterApplied = true },
                    onNavigateToSeriesDetail = { navigated = it },
                    onNavigateToTagDetail = { navigated = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Favoris").performClick()

        assertEquals(true, filterApplied)
        assertNull(navigated)
    }

    @Test
    fun une_serie_declenche_la_navigation_pas_un_changement_de_filtre() {
        var filterApplied = false
        var navigated: String? = null
        composeTestRule.setContent {
            MaterialTheme {
                LibraryTitleFlyout(
                    expanded = true,
                    onDismiss = {},
                    series = listOf("Trilogie du Vide"),
                    seriesCounts = mapOf("Trilogie du Vide" to 3),
                    tags = emptyList(),
                    tagCounts = emptyMap(),
                    onSelectAll = { filterApplied = true },
                    onSelectFavorites = { filterApplied = true },
                    onNavigateToSeriesDetail = { navigated = it },
                    onNavigateToTagDetail = {},
                )
            }
        }

        // Revele la colonne droite sans naviguer ni filtrer.
        composeTestRule.onNodeWithText("Séries").performClick()
        assertEquals(false, filterApplied)
        assertNull(navigated)

        composeTestRule.onNodeWithText("Trilogie du Vide (3)").performClick()

        assertEquals("Trilogie du Vide", navigated)
        assertEquals(false, filterApplied)
    }

    @Test
    fun le_compteur_d_une_serie_a_trois_tomes_affiche_3() {
        composeTestRule.setContent {
            MaterialTheme {
                LibraryTitleFlyout(
                    expanded = true,
                    onDismiss = {},
                    series = listOf("Trilogie du Vide"),
                    seriesCounts = mapOf("Trilogie du Vide" to 3),
                    tags = emptyList(),
                    tagCounts = emptyMap(),
                    onSelectAll = {},
                    onSelectFavorites = {},
                    onNavigateToSeriesDetail = {},
                    onNavigateToTagDetail = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Séries").performClick()

        composeTestRule.onNodeWithText("Trilogie du Vide (3)").assertExists()
    }
}
