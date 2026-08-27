package com.inktone.feature.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Bookmark
import com.inktone.domain.valueobject.Locator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Tâche 3c.6, test 5 — les 3 onglets affichent chacun leur source
 * (`annotations`/`bookmarks`, aucune nouvelle source de données), le
 * toggle ajoute ET retire. Non-régression : plus de bouton `+ Signet`
 * séparé, plus de `BookmarkListSheet` plein écran.
 */
class BookmarkPanelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun locator(chapterIndex: Int, offset: Int) =
        Locator(resourceHref = "chapitre$chapterIndex.xhtml", chapterIndex = chapterIndex, charOffset = offset)

    private fun highlightWithNote() = Annotation(
        id = "note-1", publicationId = "pub-1",
        startLocator = locator(0, 0), endLocator = locator(0, 10),
        color = AnnotationColor.YELLOW, content = "Une vraie note",
        createdAt = 0L, updatedAt = 0L,
    )

    private fun highlightWithoutNote() = Annotation(
        id = "highlight-1", publicationId = "pub-1",
        startLocator = locator(1, 0), endLocator = locator(1, 10),
        color = AnnotationColor.GREEN, content = null,
        createdAt = 0L, updatedAt = 0L,
    )

    private fun oneBookmark() = Bookmark(
        id = "bookmark-1", publicationId = "pub-1",
        locator = locator(2, 0), title = "Chapitre 3", createdAt = 0L,
    )

    @Test
    fun les_3_onglets_affichent_chacun_leur_source() {
        composeTestRule.setContent {
            BookmarkPanel(
                bookmarks = listOf(oneBookmark()),
                annotations = listOf(highlightWithNote(), highlightWithoutNote()),
                isCurrentPageBookmarked = false,
                onBookmarkClick = {},
                onAnnotationClick = {},
                onToggleBookmark = {},
                onClose = {},
                onDeleteAnnotation = {},
                onEditAnnotationNote = {},
                onDeleteBookmark = {},
                onEditBookmarkNote = {},
            )
        }

        // Onglet Notes (par defaut) : seule l'annotation avec content non
        // vide y apparait.
        composeTestRule.onNodeWithText("Une vraie note").assertExists()

        composeTestRule.onNodeWithText("Surlignages").performClick()
        composeTestRule.onNodeWithText("Chapitre 2").assertExists() // startLocator.chapterIndex 1 + 1

        composeTestRule.onNodeWithText("Signets").performClick()
        composeTestRule.onNodeWithText("Chapitre 3").assertExists()
    }

    /** Lot 24, tâche 7 — le swipe complète le tap sur onglet, ne le remplace pas. */
    @Test
    fun un_swipe_horizontal_change_l_onglet_affiche() {
        composeTestRule.setContent {
            BookmarkPanel(
                bookmarks = listOf(oneBookmark()),
                annotations = listOf(highlightWithNote(), highlightWithoutNote()),
                isCurrentPageBookmarked = false,
                onBookmarkClick = {},
                onAnnotationClick = {},
                onToggleBookmark = {},
                onClose = {},
                onDeleteAnnotation = {},
                onEditAnnotationNote = {},
                onDeleteBookmark = {},
                onEditBookmarkNote = {},
            )
        }

        // Onglet Notes par défaut.
        composeTestRule.onNodeWithText("Une vraie note").assertExists()

        // Swipe vers la gauche : avance vers l'onglet suivant (Surlignages).
        composeTestRule.onRoot().performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Chapitre 2").assertExists() // startLocator.chapterIndex 1 + 1
    }

    /**
     * Lot 24, tâche 8 — pas de désynchronisation entre les deux
     * interactions : un tap sur un onglet après un swipe doit toujours
     * mener au bon contenu.
     */
    @Test
    fun tap_sur_un_onglet_apres_un_swipe_reste_synchronise() {
        composeTestRule.setContent {
            BookmarkPanel(
                bookmarks = listOf(oneBookmark()),
                annotations = listOf(highlightWithNote(), highlightWithoutNote()),
                isCurrentPageBookmarked = false,
                onBookmarkClick = {},
                onAnnotationClick = {},
                onToggleBookmark = {},
                onClose = {},
                onDeleteAnnotation = {},
                onEditAnnotationNote = {},
                onDeleteBookmark = {},
                onEditBookmarkNote = {},
            )
        }

        composeTestRule.onRoot().performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Chapitre 2").assertExists() // Surlignages, après swipe

        composeTestRule.onNodeWithText("Notes").performClick()
        composeTestRule.onNodeWithText("Une vraie note").assertExists()
    }

    @Test
    fun toggle_ajoute_et_retire_selon_isCurrentPageBookmarked() {
        var toggled = false

        composeTestRule.setContent {
            BookmarkPanel(
                bookmarks = emptyList(),
                annotations = emptyList(),
                isCurrentPageBookmarked = false,
                onBookmarkClick = {},
                onAnnotationClick = {},
                onToggleBookmark = { toggled = true },
                onClose = {},
                onDeleteAnnotation = {},
                onEditAnnotationNote = {},
                onDeleteBookmark = {},
                onEditBookmarkNote = {},
            )
        }

        composeTestRule.onNodeWithText("Marquer cette page").assertExists()
        composeTestRule.onNodeWithText("Marquer cette page").performClick()
        assertEquals(true, toggled)
    }

    @Test
    fun toggle_actif_propose_de_retirer_le_signet() {
        composeTestRule.setContent {
            BookmarkPanel(
                bookmarks = listOf(oneBookmark()),
                annotations = emptyList(),
                isCurrentPageBookmarked = true,
                onBookmarkClick = {},
                onAnnotationClick = {},
                onToggleBookmark = {},
                onClose = {},
                onDeleteAnnotation = {},
                onEditAnnotationNote = {},
                onDeleteBookmark = {},
                onEditBookmarkNote = {},
            )
        }

        composeTestRule.onNodeWithText("Page marquée — retirer").assertExists()
        composeTestRule.onNodeWithText("Marquer cette page").assertDoesNotExist()
    }

    @Test
    fun fermer_declenche_onClose() {
        var closed = false
        composeTestRule.setContent {
            BookmarkPanel(
                bookmarks = emptyList(),
                annotations = emptyList(),
                isCurrentPageBookmarked = false,
                onBookmarkClick = {},
                onAnnotationClick = {},
                onToggleBookmark = {},
                onClose = { closed = true },
                onDeleteAnnotation = {},
                onEditAnnotationNote = {},
                onDeleteBookmark = {},
                onEditBookmarkNote = {},
            )
        }

        composeTestRule.onNodeWithText("Marque-pages et notes").assertExists()
        composeTestRule.onNodeWithContentDescription("Fermer les marque-pages").performClick()
        assertEquals(true, closed)
    }

    @Test
    fun supprimer_une_note_declenche_onDeleteAnnotation() {
        var deletedId: String? = null
        composeTestRule.setContent {
            BookmarkPanel(
                bookmarks = emptyList(),
                annotations = listOf(highlightWithNote()),
                isCurrentPageBookmarked = false,
                onBookmarkClick = {},
                onAnnotationClick = {},
                onToggleBookmark = {},
                onClose = {},
                onDeleteAnnotation = { deletedId = it.id },
                onEditAnnotationNote = {},
                onDeleteBookmark = {},
                onEditBookmarkNote = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Supprimer la note").performClick()
        assertEquals("note-1", deletedId)
    }

    @Test
    fun modifier_une_note_declenche_onEditAnnotationNote() {
        var editedId: String? = null
        composeTestRule.setContent {
            BookmarkPanel(
                bookmarks = emptyList(),
                annotations = listOf(highlightWithNote()),
                isCurrentPageBookmarked = false,
                onBookmarkClick = {},
                onAnnotationClick = {},
                onToggleBookmark = {},
                onClose = {},
                onDeleteAnnotation = {},
                onEditAnnotationNote = { editedId = it.id },
                onDeleteBookmark = {},
                onEditBookmarkNote = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Modifier la note").performClick()
        assertEquals("note-1", editedId)
    }

    @Test
    fun supprimer_un_surlignage_declenche_onDeleteAnnotation() {
        var deletedId: String? = null
        composeTestRule.setContent {
            BookmarkPanel(
                bookmarks = emptyList(),
                annotations = listOf(highlightWithoutNote()),
                isCurrentPageBookmarked = false,
                onBookmarkClick = {},
                onAnnotationClick = {},
                onToggleBookmark = {},
                onClose = {},
                onDeleteAnnotation = { deletedId = it.id },
                onEditAnnotationNote = {},
                onDeleteBookmark = {},
                onEditBookmarkNote = {},
            )
        }

        composeTestRule.onNodeWithText("Surlignages").performClick()
        composeTestRule.onNodeWithContentDescription("Supprimer le surlignage").performClick()
        assertEquals("highlight-1", deletedId)
    }

    @Test
    fun supprimer_un_marque_page_declenche_onDeleteBookmark() {
        var deletedId: String? = null
        composeTestRule.setContent {
            BookmarkPanel(
                bookmarks = listOf(oneBookmark()),
                annotations = emptyList(),
                isCurrentPageBookmarked = false,
                onBookmarkClick = {},
                onAnnotationClick = {},
                onToggleBookmark = {},
                onClose = {},
                onDeleteAnnotation = {},
                onEditAnnotationNote = {},
                onDeleteBookmark = { deletedId = it.id },
                onEditBookmarkNote = {},
            )
        }

        composeTestRule.onNodeWithText("Signets").performClick()
        composeTestRule.onNodeWithContentDescription("Supprimer le marque-page").performClick()
        assertEquals("bookmark-1", deletedId)
    }

    @Test
    fun modifier_un_marque_page_declenche_onEditBookmarkNote() {
        var editedId: String? = null
        composeTestRule.setContent {
            BookmarkPanel(
                bookmarks = listOf(oneBookmark()),
                annotations = emptyList(),
                isCurrentPageBookmarked = false,
                onBookmarkClick = {},
                onAnnotationClick = {},
                onToggleBookmark = {},
                onClose = {},
                onDeleteAnnotation = {},
                onEditAnnotationNote = {},
                onDeleteBookmark = {},
                onEditBookmarkNote = { editedId = it.id },
            )
        }

        composeTestRule.onNodeWithText("Signets").performClick()
        composeTestRule.onNodeWithContentDescription("Modifier la note").performClick()
        assertEquals("bookmark-1", editedId)
    }
}
