package com.inktone.ui.screen.reader

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.data.database.AnnotationDao
import com.inktone.data.database.BookmarkDao
import com.inktone.data.database.HighlightDao
import com.inktone.data.database.ReadingSessionDao
import com.inktone.data.database.SentenceCacheDao
import com.inktone.data.settings.SettingsRepository
import com.inktone.domain.model.Book
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.Sentence
import com.inktone.domain.repository.BookRepository
import com.inktone.domain.repository.TtsRepository
import com.inktone.domain.service.AudioServiceLauncher
import com.inktone.domain.usecase.CalculateReadingProgressUseCase
import com.inktone.domain.usecase.ChapterWithAnnotations
import com.inktone.domain.usecase.LoadChapterUseCase
import com.inktone.domain.usecase.ManageReaderAnnotationsUseCase
import com.inktone.domain.usecase.PreWarmNextChapterUseCase
import com.inktone.domain.usecase.ResolveReadingPositionUseCase
import com.inktone.domain.usecase.ResolvedPosition
import com.inktone.service.audio.PlaybackOrchestrator
import com.inktone.service.audio.PlaybackState
import com.inktone.service.onnx.OnnxInferenceService
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test d'intégration UI du scénario de reprise de lecture (PLAN_ACTION_TOP_TIER_CLAUDECODE.md
 * §1.8/§6.2) : un livre rouvert à une position non-nulle doit afficher immédiatement la phrase
 * restaurée, sans presser Play — régression historique de la Phase 1 (`activeIdx` figé sur
 * `playbackState.activeSentenceIndex`, `startFrom` codé en dur à 0 dans `play()`).
 *
 * `ReaderViewModel` est construit avec ses 17 dépendances mockées (même stratégie que
 * `ReaderViewModelTest.kt`, dupliquée ici plutôt que partagée car les deux tests tournent dans
 * des source sets différents — JVM vs instrumenté), et injecté directement dans `ReaderScreen`
 * via son paramètre `viewModel`, sans passer par Hilt (`ReaderScreen` accepte un `ReaderViewModel`
 * en paramètre par défaut `hiltViewModel()`, ce qui permet de le substituer directement en test).
 */
@RunWith(AndroidJUnit4::class)
class ReaderScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildViewModel(resumeSentenceIndex: Int): ReaderViewModel {
        val savedState = SavedStateHandle()
        val bookRepository = mockk<BookRepository>(relaxed = true)
        val orchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val onnxService = mockk<OnnxInferenceService>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
        val highlightDao = mockk<HighlightDao>(relaxed = true)
        val annotationDao = mockk<AnnotationDao>(relaxed = true)
        val sentenceCacheDao = mockk<SentenceCacheDao>(relaxed = true)
        val readingSessionDao = mockk<ReadingSessionDao>(relaxed = true)
        val audioServiceLauncher = mockk<AudioServiceLauncher>(relaxed = true)
        val ttsRepository = mockk<TtsRepository>(relaxed = true)
        val calculateProgress = mockk<CalculateReadingProgressUseCase>(relaxed = true)
        val loadChapterUseCase = mockk<LoadChapterUseCase>(relaxed = true)
        val annotationsUseCase = mockk<ManageReaderAnnotationsUseCase>(relaxed = true)
        val preWarmChapter = mockk<PreWarmNextChapterUseCase>(relaxed = true)
        val resolvePosition = mockk<ResolveReadingPositionUseCase>(relaxed = true)

        val book = Book(
            id = "book-1", title = "Les Misérables", author = "Victor Hugo", description = null,
            coverPath = null, totalChapters = 1, language = "fr", addedAt = 0L
        )
        val sentences = listOf(
            Sentence(index = 0, text = "Bonjour.", startOffset = 0, endOffset = 8),
            Sentence(index = 1, text = "Comment allez-vous ?", startOffset = 9, endOffset = 28),
            Sentence(index = 2, text = "Très bien merci.", startOffset = 29, endOffset = 45)
        )
        val chapter = Chapter(index = 0, title = "Chapitre 1", sentences = sentences)

        every { settingsRepository.readerTheme } returns flowOf("NIGHT")
        every { settingsRepository.readerFont } returns flowOf("SERIF")
        every { settingsRepository.fontSize } returns flowOf(18f)
        every { settingsRepository.lineHeight } returns flowOf(1.8f)
        every { settingsRepository.horizontalMargin } returns flowOf(24)
        every { settingsRepository.hasSeenReaderTooltip } returns flowOf(true)
        every { settingsRepository.hasSeenPlayTooltip } returns flowOf(true)
        every { settingsRepository.voice } returns flowOf("Jessica")
        coEvery { readingSessionDao.getAllSync() } returns emptyList()

        every { orchestrator.state } returns MutableStateFlow(PlaybackOrchestrator.State.Idle)
        every { orchestrator.playbackState } returns MutableStateFlow(PlaybackState())
        every { orchestrator.sleepTimerRemaining } returns MutableStateFlow(null)
        every { audioServiceLauncher.canStart() } returns true
        every { audioServiceLauncher.start() } just Runs
        coEvery { onnxService.initialize() } just Runs

        coEvery { bookRepository.getAllBooks() } returns listOf(book)
        coEvery { orchestrator.loadProgress("book-1") } returns null
        every {
            resolvePosition.invoke(any(), any(), any(), any(), any())
        } returns ResolvedPosition(0, resumeSentenceIndex)
        coEvery { loadChapterUseCase.invoke("book-1", 0) } returns ChapterWithAnnotations(
            chapter = chapter, highlights = emptyList(), bookmarks = emptyList(), annotations = emptyList()
        )
        every { highlightDao.getHighlightsForChapter(any(), any()) } returns flowOf(emptyList())
        every { bookmarkDao.getBookmarks(any()) } returns flowOf(emptyList())

        return ReaderViewModel(
            savedState = savedState,
            bookRepository = bookRepository,
            orchestrator = orchestrator,
            onnxService = onnxService,
            settingsRepository = settingsRepository,
            bookmarkDao = bookmarkDao,
            highlightDao = highlightDao,
            annotationDao = annotationDao,
            sentenceCacheDao = sentenceCacheDao,
            readingSessionDao = readingSessionDao,
            audioServiceLauncher = audioServiceLauncher,
            ttsRepository = ttsRepository,
            calculateProgress = calculateProgress,
            loadChapterUseCase = loadChapterUseCase,
            annotationsUseCase = annotationsUseCase,
            preWarmChapter = preWarmChapter,
            resolvePosition = resolvePosition
        )
    }

    @Test
    fun readerScreenShowsRestoredPositionWithoutPressingPlay() {
        val viewModel = buildViewModel(resumeSentenceIndex = 2)

        composeTestRule.setContent {
            ReaderScreen(bookId = "book-1", onBack = {}, viewModel = viewModel)
        }

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.uiState.value.currentChapter != null
        }
        // Phrase restaurée (index 2) visible sans avoir déclenché play() — garde-fou contre la
        // régression corrigée en §1.5 (source de vérité affichée figée sur playbackState) et
        // §1.6 (startFrom codé en dur à 0).
        composeTestRule.onNodeWithText("Très bien merci.").assertExists()
        assert(!viewModel.uiState.value.isPlaying) { "isPlaying ne doit pas être vrai sans action Play" }
        assert(viewModel.uiState.value.currentSentenceIndex == 2) {
            "currentSentenceIndex attendu = 2, obtenu = ${viewModel.uiState.value.currentSentenceIndex}"
        }
    }
}
