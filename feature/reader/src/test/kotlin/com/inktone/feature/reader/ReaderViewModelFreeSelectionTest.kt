package com.inktone.feature.reader

import com.inktone.core.testing.fake.FakeAnnotationRepository
import com.inktone.core.testing.fake.FakeBookmarkRepository
import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeThemeRepository
import com.inktone.core.testing.fake.FakeTtsEngine
import com.inktone.core.testing.fake.FakeVoiceProfileRepository
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.StyledText
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationMetadata
import com.inktone.domain.usecase.AddAnnotationUseCase
import com.inktone.domain.usecase.CreateBookmarkUseCase
import com.inktone.domain.usecase.DeleteBookmarkUseCase
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.GetVoiceProfilesUseCase
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
import org.junit.Before
import org.junit.Test
import com.inktone.core.testing.fake.FakeChapterParser
import com.inktone.core.testing.fake.FakeEpubResourceResolver

/**
 * Sélection libre au mot : `SetFreeSelection`/`ClearFreeSelection` et
 * branchement de `ConfirmAnnotation` sur les offsets de caractère exacts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelFreeSelectionTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun oneSentenceChapter() = Chapter(
        index = 0,
        href = "OEBPS/chapter1.xhtml",
        title = null,
        content = ChapterContent.Rich(
            blocks = listOf(
                BookBlock.ParagraphBlock(
                    richText = StyledText.plain("Bonjour le monde."),
                    globalOffsetRange = 0 until 18,
                ),
            ),
        ),
        sentences = listOf(Sentence(index = 0, text = "Bonjour le monde.", startOffset = 0, endOffset = 18)),
    )

    private suspend fun buildViewModel(
        readingStateRepository: FakeReadingStateRepository,
        publicationRepository: FakePublicationRepository,
        bookmarkRepository: FakeBookmarkRepository,
        annotationRepository: FakeAnnotationRepository,
    ): ReaderViewModel {
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(eyeRestReminderEnabled = false))
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
            playbackOrchestrator = PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(readingStateRepository), GetReadingStateUseCase(readingStateRepository), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository()),
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
            getVoiceProfiles = GetVoiceProfilesUseCase(FakeVoiceProfileRepository()),
            readingSessionRepository = FakeReadingSessionRepository(),
            themeRepository = FakeThemeRepository(),
            fixedPageRenderer = com.inktone.core.testing.fake.FakeFixedPageRenderer(),
            chapterParser = FakeChapterParser(),
            epubResourceResolver = FakeEpubResourceResolver(),
            narrationSessionContinuation = NarrationSessionContinuation(
                PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository()),
                FakeReadingSessionRepository(),
            ),
        )
    }

    private suspend fun openTestPublication(
        viewModel: ReaderViewModel,
        publicationRepository: FakePublicationRepository,
    ) {
        publicationRepository.insert(
            Publication(
                id = "pub-1", title = "Test", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 1,
                importDate = 0L,
            ),
        )
        viewModel.onIntent(ReaderIntent.OpenPublication("pub-1"))
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun setFreeSelection_expose_la_plage_min_max_independamment_de_l_ordre() = runTest {
        val readingStateRepository = FakeReadingStateRepository()
        val publicationRepository = FakePublicationRepository()
        val viewModel = buildViewModel(readingStateRepository, publicationRepository, FakeBookmarkRepository(), FakeAnnotationRepository())
        openTestPublication(viewModel, publicationRepository)

        // Glissement vers la gauche : focus avant l'ancre.
        viewModel.onIntent(ReaderIntent.SetFreeSelection(anchorOffset = 11, focusOffset = 2))
        dispatcher.scheduler.runCurrent()

        assertEquals(2..11, viewModel.state.value.freeSelectionRange)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun clearFreeSelection_efface_la_plage() = runTest {
        val readingStateRepository = FakeReadingStateRepository()
        val publicationRepository = FakePublicationRepository()
        val viewModel = buildViewModel(readingStateRepository, publicationRepository, FakeBookmarkRepository(), FakeAnnotationRepository())
        openTestPublication(viewModel, publicationRepository)

        viewModel.onIntent(ReaderIntent.SetFreeSelection(0, 5))
        dispatcher.scheduler.runCurrent()
        viewModel.onIntent(ReaderIntent.ClearFreeSelection)
        dispatcher.scheduler.runCurrent()

        assertNull(viewModel.state.value.freeSelectionRange)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun confirmAnnotation_avec_selection_libre_active_resout_les_offsets_de_caractere_pas_la_phrase_entiere() = runTest {
        val readingStateRepository = FakeReadingStateRepository()
        val publicationRepository = FakePublicationRepository()
        val annotationRepository = FakeAnnotationRepository()
        val viewModel = buildViewModel(readingStateRepository, publicationRepository, FakeBookmarkRepository(), annotationRepository)
        openTestPublication(viewModel, publicationRepository)

        // "monde" : offsets locaux 11-15 inclus (endOffsetExclusive 16).
        viewModel.onIntent(ReaderIntent.SetFreeSelection(anchorOffset = 11, focusOffset = 15))
        viewModel.onIntent(ReaderIntent.ConfirmAnnotation(AnnotationColor.YELLOW))
        dispatcher.scheduler.runCurrent()

        val annotation = viewModel.state.value.annotations.single()
        assertEquals(11, annotation.startLocator.charOffset)
        assertEquals(16, annotation.endLocator.charOffset)
        assertEquals("monde", annotation.excerpt)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun confirmAnnotation_reinitialise_la_selection_libre() = runTest {
        val readingStateRepository = FakeReadingStateRepository()
        val publicationRepository = FakePublicationRepository()
        val annotationRepository = FakeAnnotationRepository()
        val viewModel = buildViewModel(readingStateRepository, publicationRepository, FakeBookmarkRepository(), annotationRepository)
        openTestPublication(viewModel, publicationRepository)

        viewModel.onIntent(ReaderIntent.SetFreeSelection(0, 6))
        viewModel.onIntent(ReaderIntent.ConfirmAnnotation(AnnotationColor.YELLOW))
        dispatcher.scheduler.runCurrent()

        assertNull(viewModel.state.value.freeSelectionRange)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    /**
     * Garde-fou du contrat de synchronicité de
     * [ReaderViewModel.confirmAnnotation] (Phase 4 de la refonte du cycle
     * de vie de la sélection) : l'UI purge son état de sélection —
     * `ClearFreeSelection` — dans le MÊME callback que l'action du popup,
     * juste après avoir dispatché `ConfirmAnnotation`, sans attendre
     * l'écriture en base. L'annotation doit quand même être créée avec les
     * offsets exacts. Ce test échoue si la lecture de `freeSelectionRange`
     * ou la résolution des locators repasse un jour dans le
     * `viewModelScope.launch`.
     */
    @Test
    fun confirmAnnotation_puis_clearFreeSelection_immediat_cree_quand_meme_l_annotation() = runTest {
        val readingStateRepository = FakeReadingStateRepository()
        val publicationRepository = FakePublicationRepository()
        val annotationRepository = FakeAnnotationRepository()
        val viewModel = buildViewModel(readingStateRepository, publicationRepository, FakeBookmarkRepository(), annotationRepository)
        openTestPublication(viewModel, publicationRepository)

        // "monde" : offsets locaux 11-15 inclus (endOffsetExclusive 16).
        viewModel.onIntent(ReaderIntent.SetFreeSelection(anchorOffset = 11, focusOffset = 15))
        viewModel.onIntent(ReaderIntent.ConfirmAnnotation(AnnotationColor.YELLOW))
        // Purge synchrone par l'UI, avant que la coroutine d'écriture ne tourne.
        viewModel.onIntent(ReaderIntent.ClearFreeSelection)
        dispatcher.scheduler.runCurrent()

        val annotation = viewModel.state.value.annotations.single()
        assertEquals(11, annotation.startLocator.charOffset)
        assertEquals(16, annotation.endLocator.charOffset)
        assertEquals("monde", annotation.excerpt)
        assertNull(viewModel.state.value.freeSelectionRange)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun confirmAnnotation_sans_aucune_selection_active_ne_fait_rien() = runTest {
        val readingStateRepository = FakeReadingStateRepository()
        val publicationRepository = FakePublicationRepository()
        val annotationRepository = FakeAnnotationRepository()
        val viewModel = buildViewModel(readingStateRepository, publicationRepository, FakeBookmarkRepository(), annotationRepository)
        openTestPublication(viewModel, publicationRepository)

        viewModel.onIntent(ReaderIntent.ConfirmAnnotation(AnnotationColor.YELLOW))
        dispatcher.scheduler.runCurrent()

        assertEquals(0, viewModel.state.value.annotations.size)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }
}
