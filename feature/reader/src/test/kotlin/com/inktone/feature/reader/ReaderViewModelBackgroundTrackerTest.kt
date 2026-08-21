package com.inktone.feature.reader

import com.inktone.core.testing.fake.FakeAnnotationRepository
import com.inktone.core.testing.fake.FakeBookmarkRepository
import com.inktone.core.testing.fake.FakeChapterParser
import com.inktone.core.testing.fake.FakeEpubResourceResolver
import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeThemeRepository
import com.inktone.core.testing.fake.FakeTtsEngine
import com.inktone.core.testing.fake.FakeVoiceProfileRepository
import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.StyledText
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
import org.junit.Before
import org.junit.Test

/**
 * P1 (plan polissage Pareto) — garde-fou du correctif de `onAppBackground`.
 *
 * Avant le correctif, `onAppBackground()` pausait le tracker de session
 * **inconditionnellement** (`sessionTracker?.pause()`), y compris pendant une
 * écoute TTS active : le temps d'écoute écran éteint n'était donc jamais
 * comptabilisé dans les statistiques. Le correctif ne pause que lorsque
 * `!isPlaying` — l'écoute en arrière-plan reste imputée au mode AUDIO.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelBackgroundTrackerTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    /** Chapitre unique : `hasNextChapter = false`, donc aucune auto-avance parasite. */
    private fun singleChapter() = Chapter(
        index = 0,
        href = "OEBPS/chapter0.xhtml",
        title = null,
        content = ChapterContent.Rich(
            blocks = listOf(
                BookBlock.ParagraphBlock(
                    richText = StyledText.plain("Une seule phrase."),
                    globalOffsetRange = 0 until 18,
                ),
            ),
        ),
        sentences = listOf(Sentence(index = 0, text = "Une seule phrase.", startOffset = 0, endOffset = 18)),
    )

    private suspend fun buildViewModel(
        publicationRepository: FakePublicationRepository,
    ): ReaderViewModel {
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(eyeRestReminderEnabled = false))
        val parser = FakePublicationParser(
            result = ParseResult.Success(
                documentModel = DocumentModel(
                    chapters = listOf(singleChapter()),
                    tableOfContents = emptyList(),
                    resources = emptyList(),
                ),
                isDrmProtected = false,
                metadata = PublicationMetadata(title = "Titre de test"),
            ),
        )

        return ReaderViewModel(
            ttsEngine = FakeTtsEngine(),
            playbackOrchestrator = PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser()),
            publicationParser = parser,
            updateReadingState = UpdateReadingStateUseCase(FakeReadingStateRepository()),
            getReadingState = GetReadingStateUseCase(FakeReadingStateRepository()),
            publicationRepository = publicationRepository,
            preferencesRepository = preferencesRepository,
            annotationRepository = FakeAnnotationRepository(),
            addAnnotation = AddAnnotationUseCase(FakeAnnotationRepository()),
            bookmarkRepository = FakeBookmarkRepository(),
            createBookmark = CreateBookmarkUseCase(FakeBookmarkRepository()),
            deleteBookmark = DeleteBookmarkUseCase(FakeBookmarkRepository()),
            voiceProfileRepository = FakeVoiceProfileRepository(),
            getVoiceProfiles = GetVoiceProfilesUseCase(FakeVoiceProfileRepository()),
            readingSessionRepository = FakeReadingSessionRepository(),
            themeRepository = FakeThemeRepository(),
            fixedPageRenderer = com.inktone.core.testing.fake.FakeFixedPageRenderer(),
            chapterParser = FakeChapterParser(),
            epubResourceResolver = FakeEpubResourceResolver(),
            narrationSessionContinuation = NarrationSessionContinuation(
                PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser()),
                FakeReadingSessionRepository(),
            ),
        )
    }

    private suspend fun openTestPublication(viewModel: ReaderViewModel, publicationRepository: FakePublicationRepository) {
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
    fun onAppBackground_sans_ecoute_pause_le_tracker() = runTest {
        val publicationRepository = FakePublicationRepository()
        val viewModel = buildViewModel(publicationRepository)
        openTestPublication(viewModel, publicationRepository)

        // Tracker actif (VISUAL) à l'ouverture, pas encore pausé.
        assertEquals(false, viewModel.isSessionTrackerPausedForTest())

        viewModel.onAppBackground()

        // Pas d'écoute TTS : le tracker doit être pausé (temps en arrière-plan
        // non compté comme lecture).
        assertEquals(true, viewModel.isSessionTrackerPausedForTest())

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun onAppBackground_pendant_ecoute_tts_laisse_le_tracker_actif() = runTest {
        val publicationRepository = FakePublicationRepository()
        val viewModel = buildViewModel(publicationRepository)
        openTestPublication(viewModel, publicationRepository)

        // isPlaying passe à vrai SYNCHRONEMENT dans playCurrentSentence (avant
        // la coroutine qui lance l'ordonnanceur sur Dispatchers.IO) — on
        // n'avance pas le scheduler pour ne pas déclencher la lecture réelle.
        viewModel.onIntent(ReaderIntent.PlayCurrentSentence)
        assertEquals(true, viewModel.state.value.isPlaying)

        viewModel.onAppBackground()

        // Écoute TTS active : le tracker ne doit PAS être pausé — le temps
        // d'écoute en arrière-plan reste imputé au mode AUDIO.
        assertEquals(false, viewModel.isSessionTrackerPausedForTest())

        viewModel.cancelCheckpointTimerForTest()
        // Pause explicite pour vider l'état avant la fin du test.
        viewModel.onIntent(ReaderIntent.Pause)
        dispatcher.scheduler.runCurrent()
    }
}
