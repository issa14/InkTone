package com.inktone.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Lot 2b.5 — mode liste (UX §Bibliothèque état peuplé, disposition
 * liste) : cœur et 3-points côte à côte, barre de progression pleine
 * largeur reflétant `progressMap`.
 */
class PublicationListRowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun publication() = Publication(
        id = "pub-1", title = "Germinal", authors = listOf("Émile Zola"),
        format = PublicationFormat.EPUB, fileUri = "content://fake/1", fileHash = "hash-1",
        fileSize = 100L, chapterCount = 3, importDate = 0L,
    )

    @Test
    fun coeur_et_3_points_sont_presents_et_cliquables_sur_la_meme_rangee() {
        var favToggled = false
        var actionsOpened = false
        composeTestRule.setContent {
            MaterialTheme {
                PublicationListRow(
                    publication = publication(),
                    onClick = {},
                    onToggleFavorite = { favToggled = true },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Ajouter aux favoris").performClick()
        assertEquals(true, favToggled)

        composeTestRule.onNodeWithContentDescription("Actions sur « Germinal »").performClick()
        composeTestRule.onNodeWithText("Épingler").assertExists()
    }

    @Test
    fun la_barre_de_progression_reflete_le_pourcentage_fourni() {
        composeTestRule.setContent {
            MaterialTheme {
                PublicationListRow(
                    publication = publication(),
                    onClick = {},
                    onToggleFavorite = {},
                    progressPercent = 42,
                )
            }
        }

        composeTestRule.onNodeWithText("42%").assertExists()
    }

    @Test
    fun aucune_barre_de_progression_si_non_commence() {
        composeTestRule.setContent {
            MaterialTheme {
                PublicationListRow(
                    publication = publication(),
                    onClick = {},
                    onToggleFavorite = {},
                    progressPercent = 0,
                )
            }
        }

        composeTestRule.onNodeWithText("0%").assertDoesNotExist()
    }
}
