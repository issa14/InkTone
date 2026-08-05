package com.inktone.feature.reader

import com.inktone.core.testing.fake.FakeAnnotationRepository
import com.inktone.core.testing.fake.FakeBookmarkRepository
import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeTtsEngine
import com.inktone.core.testing.fake.FakeVoiceProfileRepository
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.Paragraph
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.Sentence
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationMetadata
import com.inktone.domain.usecase.AddAnnotationUseCase
import com.inktone.domain.usecase.CreateBookmarkUseCase
import com.inktone.domain.usecase.DeleteBookmarkUseCase
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tâche 3c.3 (toggle « Marquer cette page ») et 3c.4 (« Note » persiste
 * réellement `Annotation.content`, distinct de `null`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelBookmarkToggleTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun oneSentenceChapter() = Chapter(
        index = 0,
        href = "OEBPS/chapter1.xhtml",
        title = null,
        paragraphs = listOf(
            Paragraph(
                index = 0,
                sentences = listOf(Sentence(index = 0, text = "Phrase unique.", startOffset = 0, endOffset = 14)),
            ),
        ),
    )

    private fun buildViewModel(
        readingStateRepository: FakeReadingStateRepository,
        publicationRepository: FakePublicationRepository,
        bookmarkRepository: FakeBookmarkRepository,
        annotationRepository: FakeAnnotationRepository,
    ): ReaderViewModel {
        val preferencesRepository = FakePreferencesRepository()
        val parser = FakePublicationParser(
            result = ParseResult.Success(
                documentModel = DocumentModel(
                    chapters = listOf(oneSentenceChapter()),
                    tableOfContents = emptyList(),
                    resources = emptyList(),
                ),
                isDrmProtected = false,
                metadata = PublicationMetadata(title = "Titre de test"),
            ),
        )

        return ReaderViewModel(
            ttsEngine = FakeTtsEngine(),
            audioSegmentPlayer = AudioSegmentPlayer(),
            publicationParser = parser,
            updateReadingState = UpdateReadingStateUseCase(readingStateRepository),
            getReadingState = GetReadingStateUseCase(readingStateRepository),
            publicationRepository = publicationRepository,
            preferencesRepository = preferencesRepository,
            annotationRepository = annotationRepository,
            addAnnotation = AddAnnotationUseCase(annotationRepository),
            bookmarkRepository = bookmarkRepository,
            createBookmark = CreateBookmarkUseCase(bookmarkRepository),
            deleteBookmark = DeleteBookmarkUseCase(bookmarkRepository),
            voiceProfileRepository = FakeVoiceProfileRepository(),
        )
    }

    @Test
    fun toggle_ajoute_puis_retire_le_signet_a_la_position_courante() = runTest {
        val readingStateRepository = FakeReadingStateRepository()
        val publicationRepository = FakePublicationRepository()
        val bookmarkRepository = FakeBookmarkRepository()
        val annotationRepository = FakeAnnotationRepository()
        publicationRepository.insert(
            Publication(
                id = "pub-1", title = "Test", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 1,
                importDate = 0L,
            ),
        )
        val viewModel = buildViewModel(readingStateRepository, publicationRepository, bookmarkRepository, annotationRepository)
        viewModel.onIntent(ReaderIntent.OpenPublication("pub-1"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.state.value.isCurrentPageBookmarked)

        viewModel.onIntent(ReaderIntent.ToggleBookmarkAtCurrentPosition)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.bookmarks.size)
        assertTrue("le toggle reflete l'etat de la page courante", viewModel.state.value.isCurrentPageBookmarked)

        // Jamais de doublon : un second appel retire, n'ajoute pas.
        viewModel.onIntent(ReaderIntent.ToggleBookmarkAtCurrentPosition)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.state.value.bookmarks.size)
        assertEquals(false, viewModel.state.value.isCurrentPageBookmarked)
    }

    @Test
    fun note_persiste_le_contenu_distinct_d_un_surlignage_sans_note() = runTest {
        val readingStateRepository = FakeReadingStateRepository()
        val publicationRepository = FakePublicationRepository()
        val bookmarkRepository = FakeBookmarkRepository()
        val annotationRepository = FakeAnnotationRepository()
        publicationRepository.insert(
            Publication(
                id = "pub-1", title = "Test", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 1,
                importDate = 0L,
            ),
        )
        val viewModel = buildViewModel(readingStateRepository, publicationRepository, bookmarkRepository, annotationRepository)
        viewModel.onIntent(ReaderIntent.OpenPublication("pub-1"))
        dispatcher.scheduler.advanceUntilIdle()

        // Surlignage sans note (action « Surligner » du popup 3c.4).
        viewModel.onIntent(ReaderIntent.BeginSentenceSelection(0))
        viewModel.onIntent(ReaderIntent.ConfirmAnnotation(AnnotationColor.YELLOW))
        dispatcher.scheduler.advanceUntilIdle()

        val highlightOnly = viewModel.state.value.annotations.single()
        assertNull("un surlignage sans note reste content = null", highlightOnly.content)

        // Note (action « Note » du popup 3c.4) : contenu reellement rempli
        // et relu depuis l'etat observe.
        viewModel.onIntent(ReaderIntent.BeginSentenceSelection(0))
        viewModel.onIntent(ReaderIntent.ConfirmAnnotation(AnnotationColor.GREEN, "Ma note de lecture"))
        dispatcher.scheduler.advanceUntilIdle()

        val withNote = viewModel.state.value.annotations.first { it.content != null }
        assertEquals("Ma note de lecture", withNote.content)
    }
}
