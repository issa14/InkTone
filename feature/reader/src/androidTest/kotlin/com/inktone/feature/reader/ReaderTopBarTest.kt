package com.inktone.feature.reader

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
}
