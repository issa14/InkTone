package com.inktone.feature.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.inktone.domain.model.TableOfContentsEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Tâche 3c.6, test 4 — sommaire en bottom sheet, hiérarchie sur une
 * fixture à structure imbriquée (Tome > Chapitre > titre réel), même
 * forme que celle prouvée non vide en production par
 * `TableOfContentsChildrenTest` (`infrastructure/parser`, Tâche 4.11).
 */
class TableOfContentsSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun nestedFixture() = listOf(
        TableOfContentsEntry(
            title = "Tome I",
            chapterIndex = 0,
            children = listOf(
                TableOfContentsEntry(title = "Chapitre I", chapterIndex = 0),
                TableOfContentsEntry(title = "Chapitre II", chapterIndex = 1),
            ),
        ),
        TableOfContentsEntry(title = "Tome II", chapterIndex = 2),
    )

    @Test
    fun titre_cible_confirme_table_des_matieres() {
        composeTestRule.setContent {
            TableOfContentsSheet(
                entries = nestedFixture(),
                currentChapterIndex = 0,
                onEntryClick = {},
                onClose = {},
            )
        }

        composeTestRule.onNodeWithText("Table des matières").assertExists()
    }

    @Test
    fun hierarchie_imbriquee_affiche_parent_et_enfants() {
        composeTestRule.setContent {
            TableOfContentsSheet(
                entries = nestedFixture(),
                currentChapterIndex = 0,
                onEntryClick = {},
                onClose = {},
            )
        }

        // Les 4 entrées (1 parent + 2 enfants + 1 entrée plate) sont
        // toutes rendues — l'aplatissement (flattenWithDepth) ne perd
        // aucune entrée de la hiérarchie source.
        composeTestRule.onNodeWithText("Tome I").assertExists()
        composeTestRule.onNodeWithText("Chapitre I").assertExists()
        composeTestRule.onNodeWithText("Chapitre II").assertExists()
        composeTestRule.onNodeWithText("Tome II").assertExists()
    }

    @Test
    fun clic_sur_une_entree_remonte_son_chapterIndex() {
        var clicked: Int? = null
        composeTestRule.setContent {
            TableOfContentsSheet(
                entries = nestedFixture(),
                currentChapterIndex = 0,
                onEntryClick = { clicked = it },
                onClose = {},
            )
        }

        composeTestRule.onNodeWithText("Chapitre II").performClick()

        assertEquals(1, clicked)
    }
}
