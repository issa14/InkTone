package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakeAnnotationRepository
import com.inktone.core.testing.fake.FakeBookmarkRepository
import com.inktone.core.testing.fake.FakePreAnalysisStore
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Bookmark
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.StyledText
import com.inktone.domain.valueobject.Locator
import com.inktone.domain.valueobject.LocatorText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** ADR-027 : rattrapage du contexte puis ré-ancrage quand le texte du chapitre évolue. */
class ReanchorLocatorsUseCaseTest {

    private val oldText = "Il était une fois un roi qui avait trois fils. Le plus jeune partit seul."
    private val publications = FakePublicationRepository()
    private val store = FakePreAnalysisStore()
    private val states = FakeReadingStateRepository()
    private val bookmarks = FakeBookmarkRepository()
    private val annotations = FakeAnnotationRepository()

    private fun chapterWith(text: String) = Chapter(
        index = 0, href = "ch1.xhtml", title = null,
        content = ChapterContent.Rich(listOf(BookBlock.ParagraphBlock(StyledText.plain(text), 0 until text.length))),
    )

    private suspend fun book(version: Int = 0) = publications.insert(
        Publication(
            id = "pub", title = "Livre", format = PublicationFormat.EPUB, fileUri = "content://x",
            fileHash = "h", fileSize = 10, chapterCount = 1, importDate = 0L, locatorAnchorVersion = version,
        ),
    )

    private fun useCase(version: Int = 4) =
        ReanchorLocatorsUseCase(publications, store, states, bookmarks, annotations, currentParserVersion = version)

    @Test
    fun `le rattrapage donne leur contexte aux positions existantes sans les deplacer`() = runTest {
        book()
        store.save("pub", "h", listOf(chapterWith(oldText)))
        bookmarks.insert(Bookmark("b1", "pub", Locator("ch1.xhtml", 0, charOffset = 40), createdAt = 0L))
        states.save(ReadingState("pub", Locator("ch1.xhtml", 0, charOffset = 25), lastReadAt = 0L))

        val outcome = useCase()()

        assertEquals(1, outcome.books)
        val bookmark = bookmarks.observeForPublication("pub").first().single()
        assertEquals(40, bookmark.locator.charOffset)
        assertEquals(LocatorText.capture(oldText, 40), bookmark.locator.text)
        assertNotNull(states.get("pub")!!.locator.text)
        assertEquals(4, publications.getById("pub")!!.locatorAnchorVersion)
    }

    @Test
    fun `une position avec contexte suit le passage quand le texte du chapitre a change`() = runTest {
        book(version = 3)
        val newText = "Chapitre un\n" + oldText
        store.save("pub", "h", listOf(chapterWith(newText)))
        bookmarks.insert(
            Bookmark("b1", "pub", Locator("ch1.xhtml", 0, charOffset = 40, text = LocatorText.capture(oldText, 40)), createdAt = 0L),
        )

        val outcome = useCase(version = 4)()

        assertEquals(1, outcome.reanchored)
        assertEquals(40 + "Chapitre un\n".length, bookmarks.observeForPublication("pub").first().single().locator.charOffset)
    }

    @Test
    fun `un livre deja ancre ou a pre-analyse perimee n'est pas touche`() = runTest {
        book(version = 4)
        store.save("pub", "h", listOf(chapterWith(oldText)))
        bookmarks.insert(Bookmark("b1", "pub", Locator("ch1.xhtml", 0, charOffset = 40), createdAt = 0L))

        val outcome = useCase(version = 4)()

        assertEquals(0, outcome.books)
        assertNull(bookmarks.observeForPublication("pub").first().single().locator.text)

        // Pré-analyse absente : le livre attend le passage suivant.
        publications.setLocatorAnchorVersion("pub", 0)
        store.delete("pub")
        assertEquals(1, useCase()().pending)
        assertEquals(0, publications.getById("pub")!!.locatorAnchorVersion)
    }

    @Test
    fun `le traitement est idempotent`() = runTest {
        book()
        store.save("pub", "h", listOf(chapterWith(oldText)))
        bookmarks.insert(Bookmark("b1", "pub", Locator("ch1.xhtml", 0, charOffset = 40), createdAt = 0L))

        useCase()()
        val second = useCase()()

        assertEquals(0, second.books)
        assertEquals(40, bookmarks.observeForPublication("pub").first().single().locator.charOffset)
    }
}
