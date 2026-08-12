package com.inktone.feature.reader

import com.inktone.core.testing.fake.FakeAnnotationRepository
import com.inktone.core.testing.fake.FakeBookmarkRepository
import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeTtsEngine
import com.inktone.core.testing.fake.FakeVoiceProfileRepository
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.Paragraph
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
        content = ChapterContent.Legacy(
            paragraphs = listOf(
                Paragraph(
                    index = 0,
                    sentences = listOf(Sentence(index = 0, text = "Phrase unique.", startOffset = 0, endOffset = 14)),
                ),
            ),
        ),
    )

    private suspend fun buildViewModel(
        readingStateRepository: FakeReadingStateRepository,
        publicationRepository: FakePublicationRepository,
        bookmarkRepository: FakeBookmarkRepository,
        annotationRepository: FakeAnnotationRepository,
    ): ReaderViewModel {
        // 3d.5 — voir même commentaire dans ReaderViewModelScrollPositionTest :
        // le rappel de repos oculaire (activé par défaut, recurrent) rendrait
        // dispatcher.scheduler.advanceUntilIdle() non terminant. Même raison
        // pour utiliser runCurrent() plutôt qu'advanceUntilIdle() partout
        // dans ce fichier : le timer de checkpoint de session (Lot Sessions,
        // `startCheckpointTimer`) est lui aussi auto-récurrent et démarre
        // inconditionnellement dès qu'une publication est ouverte.
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
            getVoiceProfiles = GetVoiceProfilesUseCase(FakeVoiceProfileRepository()),
            readingSessionRepository = FakeReadingSessionRepository(),
            themeRepository = com.inktone.core.testing.fake.FakeThemeRepository(),
            fixedPageRenderer = com.inktone.core.testing.fake.FakeFixedPageRenderer(),
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
        dispatcher.scheduler.runCurrent()

        assertEquals(false, viewModel.state.value.isCurrentPageBookmarked)

        viewModel.onIntent(ReaderIntent.ToggleBookmarkAtCurrentPosition)
        dispatcher.scheduler.runCurrent()

        assertEquals(1, viewModel.state.value.bookmarks.size)
        assertTrue("le toggle reflete l'etat de la page courante", viewModel.state.value.isCurrentPageBookmarked)

        // Jamais de doublon : un second appel retire, n'ajoute pas.
        viewModel.onIntent(ReaderIntent.ToggleBookmarkAtCurrentPosition)
        dispatcher.scheduler.runCurrent()

        assertEquals(0, viewModel.state.value.bookmarks.size)
        assertEquals(false, viewModel.state.value.isCurrentPageBookmarked)

        // Casse le timer de checkpoint (auto-récurrent) comme le ferait
        // onCleared() sur un vrai ViewModel détruit — sinon le drain
        // implicite de fin de runTest boucle indéfiniment.
        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
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
        dispatcher.scheduler.runCurrent()

        // Surlignage sans note (action « Surligner » du popup 3c.4) —
        // "Phrase unique." fait 14 caracteres, offsets 0..13.
        viewModel.onIntent(ReaderIntent.SetFreeSelection(0, 13))
        viewModel.onIntent(ReaderIntent.ConfirmAnnotation(AnnotationColor.YELLOW))
        dispatcher.scheduler.runCurrent()

        val highlightOnly = viewModel.state.value.annotations.single()
        assertNull("un surlignage sans note reste content = null", highlightOnly.content)

        // Note (action « Note » du popup 3c.4) : contenu reellement rempli
        // et relu depuis l'etat observe.
        viewModel.onIntent(ReaderIntent.SetFreeSelection(0, 13))
        viewModel.onIntent(ReaderIntent.ConfirmAnnotation(AnnotationColor.GREEN, "Ma note de lecture"))
        dispatcher.scheduler.runCurrent()

        val withNote = viewModel.state.value.annotations.first { it.content != null }
        assertEquals("Ma note de lecture", withNote.content)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    /** Lot 4, tâche 4.2 — l'extrait vient du texte réellement affiché, pas d'un offset EPUB. */
    @Test
    fun toggle_persiste_l_extrait_du_texte_affiche_sur_le_signet() = runTest {
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
        dispatcher.scheduler.runCurrent()

        viewModel.onIntent(ReaderIntent.ToggleBookmarkAtCurrentPosition)
        dispatcher.scheduler.runCurrent()

        assertEquals("Phrase unique.", viewModel.state.value.bookmarks.single().excerpt)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    /** Lot 4, tâche 4.2 — un extrait trop long est tronqué à la création, pas à l'affichage. */
    @Test
    fun confirmAnnotation_tronque_l_extrait_au_dela_de_la_borne() = runTest {
        val longText = "a".repeat(400)
        val longChapter = Chapter(
            index = 0, href = "OEBPS/chapter1.xhtml", title = null,
            content = ChapterContent.Legacy(
                paragraphs = listOf(
                    Paragraph(
                        index = 0,
                        sentences = listOf(Sentence(index = 0, text = longText, startOffset = 0, endOffset = longText.length)),
                    ),
                ),
            ),
        )
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
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(eyeRestReminderEnabled = false))
        val parser = FakePublicationParser(
            result = ParseResult.Success(
                documentModel = DocumentModel(
                    chapters = listOf(longChapter),
                    tableOfContents = emptyList(),
                    resources = emptyList(),
                ),
                isDrmProtected = false,
                metadata = PublicationMetadata(title = "Titre de test"),
            ),
        )
        val viewModel = ReaderViewModel(
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
            getVoiceProfiles = GetVoiceProfilesUseCase(FakeVoiceProfileRepository()),
            readingSessionRepository = FakeReadingSessionRepository(),
            themeRepository = com.inktone.core.testing.fake.FakeThemeRepository(),
            fixedPageRenderer = com.inktone.core.testing.fake.FakeFixedPageRenderer(),
        )
        viewModel.onIntent(ReaderIntent.OpenPublication("pub-1"))
        dispatcher.scheduler.runCurrent()

        viewModel.onIntent(ReaderIntent.SetFreeSelection(0, longText.length - 1))
        viewModel.onIntent(ReaderIntent.ConfirmAnnotation(AnnotationColor.YELLOW))
        dispatcher.scheduler.runCurrent()

        val excerpt = viewModel.state.value.annotations.single().excerpt
        assertEquals(com.inktone.domain.model.Annotation.MAX_EXCERPT_LENGTH, excerpt?.length)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }
}
