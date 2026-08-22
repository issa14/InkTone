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
 * Tâche 3c.1 — vérifie que `ReaderIntent.UpdateScrollPosition` (émis par
 * `ReaderScreen` pendant un défilement manuel silencieux, voir
 * `topmostVisibleSentenceIndex`) tient `currentSentenceIndex` à jour
 * immédiatement (test 1 : pourcentage et compteur de page dérivent de la
 * même valeur) et persiste la position, débounced, en base — la
 * régression de l'antipattern legacy que ce lot corrige (test 3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelScrollPositionTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun threeSentenceChapter() = Chapter(
        index = 0,
        href = "OEBPS/chapter1.xhtml",
        title = null,
        content = ChapterContent.Rich(
            blocks = listOf(
                BookBlock.ParagraphBlock(
                    richText = StyledText.plain("Phrase un. Phrase deux. Phrase trois."),
                    globalOffsetRange = 0 until 37,
                ),
            ),
        ),
        sentences = listOf(
            Sentence(index = 0, text = "Phrase un.", startOffset = 0, endOffset = 10),
            Sentence(index = 1, text = "Phrase deux.", startOffset = 11, endOffset = 23),
            Sentence(index = 2, text = "Phrase trois.", startOffset = 24, endOffset = 37),
        ),
    )

    private suspend fun buildViewModel(
        readingStateRepository: FakeReadingStateRepository,
        publicationRepository: FakePublicationRepository,
    ): ReaderViewModel {
        // 3d.5 — le rappel de repos oculaire est activé par défaut et se
        // reprogramme indéfiniment (voir ReaderViewModel.resumeFromEyeRestReminder) :
        // laissé activé, dispatcher.scheduler.advanceUntilIdle() ci-dessous
        // ne terminerait jamais (la file de délais virtuels ne se vide
        // jamais). Ce test ne porte pas sur le repos oculaire, on le
        // désactive pour ne pas coupler les deux. Même raison pour
        // remplacer advanceUntilIdle() par runCurrent()/advanceTimeBy()
        // borné ci-dessous : le timer de checkpoint de session (Lot
        // Sessions, `startCheckpointTimer`) est lui aussi auto-récurrent
        // et démarre inconditionnellement à l'ouverture d'une publication.
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(eyeRestReminderEnabled = false))
        val bookmarkRepository = FakeBookmarkRepository()
        val annotationRepository = FakeAnnotationRepository()
        val parser = FakePublicationParser(
            result = ParseResult.Success(
                documentModel = DocumentModel(
                    chapters = listOf(threeSentenceChapter()),
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
            themeRepository = com.inktone.core.testing.fake.FakeThemeRepository(),
            fixedPageRenderer = com.inktone.core.testing.fake.FakeFixedPageRenderer(),
            chapterParser = FakeChapterParser(),
            epubResourceResolver = FakeEpubResourceResolver(),
            narrationSessionContinuation = NarrationSessionContinuation(
                PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository()),
                FakeReadingSessionRepository(),
            ),
        )
    }

    @Test
    fun defilement_manuel_avance_currentSentenceIndex_immediatement() = runTest {
        val readingStateRepository = FakeReadingStateRepository()
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insert(
            Publication(
                id = "pub-1", title = "Test", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 1,
                importDate = 0L,
            ),
        )
        val viewModel = buildViewModel(readingStateRepository, publicationRepository)
        viewModel.onIntent(ReaderIntent.OpenPublication("pub-1"))
        dispatcher.scheduler.runCurrent()

        assertEquals(0, viewModel.state.value.currentSentenceIndex)
        val progressionBefore = viewModel.state.value.bookProgression

        viewModel.onIntent(ReaderIntent.UpdateScrollPosition(2))

        // Reflete immediatement, sans attendre le debounce de persistance
        // (test 1 du lot : le pourcentage derive de la meme valeur que le
        // compteur de page, jamais un second calcul en retard).
        assertEquals(2, viewModel.state.value.currentSentenceIndex)
        assertEquals(true, viewModel.state.value.bookProgression > progressionBefore)

        // Casse le timer de checkpoint (auto-récurrent) comme le ferait
        // onCleared() sur un vrai ViewModel détruit — sinon le drain
        // implicite de fin de runTest boucle indéfiniment.
        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun defilement_manuel_persiste_la_position_apres_debounce_pas_avant() = runTest {
        val readingStateRepository = FakeReadingStateRepository()
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insert(
            Publication(
                id = "pub-1", title = "Test", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 1,
                importDate = 0L,
            ),
        )
        val viewModel = buildViewModel(readingStateRepository, publicationRepository)
        viewModel.onIntent(ReaderIntent.OpenPublication("pub-1"))
        dispatcher.scheduler.runCurrent()

        viewModel.onIntent(ReaderIntent.UpdateScrollPosition(2))

        // Immediatement apres l'intent, avant l'ecoulement du debounce :
        // rien n'est encore ecrit en base (throttle, pas d'ecriture a
        // chaque changement d'index).
        dispatcher.scheduler.advanceTimeBy(50)
        assertEquals(0, readingStateRepository.get("pub-1")?.locator?.charOffset ?: 0)

        // Test 3 (persistance) — antipattern legacy corrige : un
        // defilement silencieux (sans TTS) est bien persiste une fois le
        // debounce ecoule, pas seulement la derniere position TTS. Avance
        // bornee (400ms de debounce + marge) plutot qu'advanceUntilIdle() :
        // le timer de checkpoint de session, demarre par OpenPublication,
        // est auto-recurrent et viderait sinon la file indefiniment.
        dispatcher.scheduler.advanceTimeBy(500L)
        dispatcher.scheduler.runCurrent()
        val restored = readingStateRepository.get("pub-1")
        assertEquals(0, restored?.locator?.chapterIndex)
        assertEquals(24, restored?.locator?.charOffset) // Sentence(2).startOffset

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    // Le scenario K3 "defilement ignore pendant le TTS" n'est pas testable
    // ici en JUnit JVM pur : declencher une lecture reelle
    // (ReaderIntent.PlayCurrentSentence) invoque AudioSegmentPlayer, qui
    // construit un android.media.AudioTrack reel - non simule par le JVM
    // de test (« Method getMinBufferSize in android.media.AudioTrack not
    // mocked »), contrairement a Robolectric ou a un test instrumente.
    // Le garde `if (_state.value.isPlaying) return` dans
    // ReaderViewModel.updateScrollPosition() reste verifiable par lecture
    // du code ; sa couverture par un test executable est un ecart declare
    // de ce lot, a lever par un test instrumente avec appareil (androidTest).

    @Test
    fun aucune_publication_ouverte_n_ecrit_rien() = runTest {
        val readingStateRepository = FakeReadingStateRepository()
        val publicationRepository = FakePublicationRepository()
        val viewModel = buildViewModel(readingStateRepository, publicationRepository)

        viewModel.onIntent(ReaderIntent.UpdateScrollPosition(1))
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(readingStateRepository.get("pub-1"))
    }
}
