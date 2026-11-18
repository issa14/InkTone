package com.inktone.feature.reader

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tâche 3b.7, test 5 — la flèche de retour émet la sortie ; titre et auteur sont affichés depuis l'état. */
class ReaderTopBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun la_fleche_de_retour_emet_la_sortie() {
        var backClicked = false
        composeTestRule.setContent {
            ReaderTopBar(title = "Les Misérables", author = "Victor Hugo", onBack = { backClicked = true })
        }

        composeTestRule.onNodeWithContentDescription("Retour").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun titre_et_auteur_sont_affiches() {
        composeTestRule.setContent {
            ReaderTopBar(title = "Les Misérables", author = "Victor Hugo", onBack = {})
        }

        composeTestRule.onNodeWithText("Les Misérables").assertExists()
        composeTestRule.onNodeWithText("Victor Hugo").assertExists()
    }

    /** Audit UI P2-9 : marquer la page en un geste, l'état annoncé par TalkBack. */
    @Test
    fun le_signet_de_la_barre_bascule_et_annonce_son_etat() {
        // État observé, comme `uiState` en production : sa lecture recompose la barre.
        val toggled = mutableStateOf(false)
        composeTestRule.setContent {
            ReaderTopBar(
                title = "Les Misérables",
                author = "Victor Hugo",
                onBack = {},
                isPageBookmarked = { toggled.value },
                onToggleBookmark = { toggled.value = !toggled.value },
            )
        }

        composeTestRule.onNodeWithContentDescription("Marquer cette page").performClick()
        assertTrue(toggled.value)
        composeTestRule.onNodeWithContentDescription("Retirer le signet de cette page").assertExists()
    }
}
