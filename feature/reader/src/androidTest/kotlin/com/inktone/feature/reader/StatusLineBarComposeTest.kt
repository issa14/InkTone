package com.inktone.feature.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * Tâche 3b.7, test 2 — la ligne de statut affiche le compteur de pages
 * du contrat `VirtualPagination` (ici simulé par des valeurs fixes : la
 * dépendance réelle au moteur est couverte côté
 * `ChapterPaginationStateComposeTest`, test 3).
 */
class StatusLineBarComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun affiche_chapitre_page_et_progression_formatee() {
        composeTestRule.setContent {
            StatusLineBar(
                chapterNumber = 3,
                pageInChapter = 12,
                pageCountInChapter = 47,
                bookProgression = 0.347f,
            )
        }

        composeTestRule.onNodeWithText("Chapitre 3 (12/47)").assertExists()
        composeTestRule.onNodeWithText("34,7%").assertExists()
    }
}
