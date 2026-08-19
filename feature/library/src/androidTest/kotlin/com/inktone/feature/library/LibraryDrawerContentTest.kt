package com.inktone.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
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
 * Lot 1/2a.5/8 — garde-fou du critère « zéro décoration » : un item du
 * drawer qui n'appelle rien fait échouer ce test. `LibraryDrawerContent`
 * est sans état (pattern `SettingsContent`, `SettingsAccessibilityTest`).
 * Depuis le lot 8, porte 4 destinations (Récents, Bibliothèque,
 * Marque-pages et Notes, Statistiques de lecture) + 2 boutons de pied —
 * les filtres/Séries/Auteurs/Tags transitoires du lot 1 restent retirés
 * (déplacés vers le flyout du titre, 2a.3) ; Récents en revanche est une
 * vraie destination réactivée, pas un filtre.
 */
class LibraryDrawerContentTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bibliotheque_declenche_son_callback() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                LibraryDrawerContent(
                    onSelectLibrary = { clicked = true },
                    onOpenBookmarks = {},
                    onOpenStats = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Bibliothèque").performClick()

        assertEquals(true, clicked)
    }

    @Test
    fun recents_declenche_son_callback() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                LibraryDrawerContent(
                    onOpenBookmarks = {},
                    onOpenStats = {},
                    onOpenRecents = { clicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Récents").performClick()

        assertEquals(true, clicked)
    }

    @Test
    fun marque_pages_et_notes_declenche_son_callback() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                LibraryDrawerContent(
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
    fun le_drawer_n_affiche_plus_les_filtres_transitoires_du_lot_1() {
        composeTestRule.setContent {
            MaterialTheme {
                LibraryDrawerContent(
                    onOpenBookmarks = {},
                    onOpenStats = {},
                )
            }
        }

        // "Récents" retiré de cette liste au lot 8 : ce n'est plus un
        // filtre transitoire absent, mais une destination à part entière
        // (voir recents_declenche_son_callback ci-dessus).
        composeTestRule.onAllNodesWithText("Debug").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Thème").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Favoris").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Séries").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Tags").assertCountEquals(0)
    }

    @Test
    fun bibliotheque_est_l_item_actif_a_l_etat_initial() {
        composeTestRule.setContent {
            MaterialTheme {
                LibraryDrawerContent(
                    onOpenBookmarks = {},
                    onOpenStats = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Bibliothèque").assertIsSelected()
        composeTestRule.onNodeWithText("Récents").assertIsNotSelected()
        composeTestRule.onNodeWithText("Marque-pages et Notes").assertIsNotSelected()
        composeTestRule.onNodeWithText("Statistiques de lecture").assertIsNotSelected()
    }

    // Lot 18 — le surlignage suit la destination active passée en
    // paramètre, plus un `selected = true` figé sur Bibliothèque.
    @Test
    fun le_surlignage_suit_la_destination_active() {
        composeTestRule.setContent {
            MaterialTheme {
                LibraryDrawerContent(
                    onOpenBookmarks = {},
                    onOpenStats = {},
                    selected = DrawerDestination.STATISTICS,
                )
            }
        }

        composeTestRule.onNodeWithText("Statistiques de lecture").assertIsSelected()
        composeTestRule.onNodeWithText("Bibliothèque").assertIsNotSelected()
        composeTestRule.onNodeWithText("Récents").assertIsNotSelected()
        composeTestRule.onNodeWithText("Marque-pages et Notes").assertIsNotSelected()
        composeTestRule.onNodeWithText("Synchronisation").assertIsNotSelected()
        composeTestRule.onNodeWithText("Catalogues OPDS").assertIsNotSelected()
    }

    @Test
    fun un_seul_item_est_surligne_quelle_que_soit_la_destination() {
        val labelParDestination = mapOf(
            DrawerDestination.RECENTS to "Récents",
            DrawerDestination.LIBRARY to "Bibliothèque",
            DrawerDestination.BOOKMARKS to "Marque-pages et Notes",
            DrawerDestination.OPDS to "Catalogues OPDS",
            DrawerDestination.SYNC to "Synchronisation",
            DrawerDestination.STATISTICS to "Statistiques de lecture",
        )
        // `setContent` ne peut être appelé qu'une fois par test : la
        // destination est pilotée par un état, comme le fera le NavHost.
        val selected = mutableStateOf(DrawerDestination.LIBRARY)
        composeTestRule.setContent {
            MaterialTheme {
                LibraryDrawerContent(
                    onOpenBookmarks = {},
                    onOpenStats = {},
                    selected = selected.value,
                )
            }
        }

        for (destination in DrawerDestination.entries) {
            selected.value = destination
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(labelParDestination.getValue(destination)).assertIsSelected()
            labelParDestination.filterKeys { it != destination }.values.forEach { label ->
                composeTestRule.onNodeWithText(label).assertIsNotSelected()
            }
        }
    }
}
