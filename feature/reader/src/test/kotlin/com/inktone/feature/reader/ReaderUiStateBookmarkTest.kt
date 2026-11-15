package com.inktone.feature.reader

import com.inktone.domain.model.Bookmark
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.Sentence
import com.inktone.domain.valueobject.Locator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit UX D51 : « Marquer cette page » marquait une phrase, et ne
 * reconnaissait un signet que sur la phrase exactement courante.
 */
class ReaderUiStateBookmarkTest {

    private val sentences = (0 until 6).map { i ->
        Sentence(index = i, text = "Phrase $i.", startOffset = i * 10, endOffset = i * 10 + 9)
    }
    private val chapter = Chapter(
        index = 0, href = "ch1.xhtml", title = null,
        content = ChapterContent.Rich(emptyList()), sentences = sentences,
    )

    private fun bookmarkAtSentence(i: Int) = Bookmark(
        id = "b$i", publicationId = "pub",
        locator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = i * 10),
        createdAt = 0L,
    )

    private fun state(currentSentence: Int, vararg bookmarkedSentences: Int) = ReaderUiState(
        chapters = listOf(chapter),
        currentChapterIndex = 0,
        currentSentenceIndex = currentSentence,
        bookmarks = bookmarkedSentences.map(::bookmarkAtSentence),
    )

    @Test
    fun `sans page connue, seule la phrase courante compte`() {
        assertEquals("b2", state(currentSentence = 2, 2).bookmarkOnCurrentPage(null)?.id)
        assertNull(state(currentSentence = 2, 0).bookmarkOnCurrentPage(null))
    }

    @Test
    fun `un signet pose plus haut sur la meme page est reconnu apres un defilement`() {
        // Marqué à la phrase 0, puis défilé jusqu'à la phrase 2 : même page 0..3.
        val state = state(currentSentence = 2, 0)

        assertEquals("b0", state.bookmarkOnCurrentPage(pageSentences = 0..3)?.id)
        assertEquals(true, state.isBookmarkedOnPage(0..3))
    }

    @Test
    fun `un signet d'une autre page n'est pas reconnu`() {
        val state = state(currentSentence = 4, 0)

        assertNull(state.bookmarkOnCurrentPage(pageSentences = 4..5))
    }

    @Test
    fun `une page qui ne contient pas la phrase courante est ignoree`() {
        // Mesure en retard sur la position : jamais une plage fausse.
        assertNull(state(currentSentence = 4, 0).bookmarkOnCurrentPage(pageSentences = 0..3))
    }
}
