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
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.UserPreferences
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.inktone.core.testing.fake.FakeChapterParser
import com.inktone.core.testing.fake.FakeEpubResourceResolver

/**
 * 3d.6 — mécanique du rappel de repos oculaire (tâche 3d.5) : indépendant
 * du minuteur de sommeil TTS (`SleepTimerState`), popup à l'échéance,
 * "Reprendre" referme le popup et reprogramme l'intervalle complet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelEyeRestReminderTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun buildViewModel(preferencesRepository: FakePreferencesRepository, publicationRepository: FakePublicationRepository): ReaderViewModel {
        val readingStateRepository = FakeReadingStateRepository()
        val bookmarkRepository = FakeBookmarkRepository()
        val annotationRepository = FakeAnnotationRepository()
        val voiceProfileRepository = FakeVoiceProfileRepository()
        return ReaderViewModel(
            ttsEngine = FakeTtsEngine(),
            playbackOrchestrator = PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(readingStateRepository), FakeChapterParser()),
            publicationParser = FakePublicationParser(),
            updateReadingState = UpdateReadingStateUseCase(readingStateRepository),
            getReadingState = GetReadingStateUseCase(readingStateRepository),
            publicationRepository = publicationRepository,
            preferencesRepository = preferencesRepository,
            annotationRepository = annotationRepository,
            addAnnotation = AddAnnotationUseCase(annotationRepository),
            bookmarkRepository = bookmarkRepository,
            createBookmark = CreateBookmarkUseCase(bookmarkRepository),
            deleteBookmark = DeleteBookmarkUseCase(bookmarkRepository),
            voiceProfileRepository = voiceProfileRepository,
            getVoiceProfiles = GetVoiceProfilesUseCase(voiceProfileRepository),
            readingSessionRepository = FakeReadingSessionRepository(),
            themeRepository = com.inktone.core.testing.fake.FakeThemeRepository(),
            fixedPageRenderer = com.inktone.core.testing.fake.FakeFixedPageRenderer(),
            chapterParser = FakeChapterParser(),
            epubResourceResolver = FakeEpubResourceResolver(),
            narrationSessionContinuation = NarrationSessionContinuation(
                PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser()),
                FakeReadingSessionRepository(),
            ),
        )
    }

    @Test
    fun le_popup_apparait_a_l_echeance_puis_reprendre_le_referme_et_reprogramme() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(eyeRestReminderEnabled = true, eyeRestReminderIntervalMinutes = 1))
        val publicationRepository = FakePublicationRepository()
        val publicationId = "pub-eyerest"
        publicationRepository.insert(
            Publication(
                id = publicationId, title = "Test", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 1,
                importDate = 0L,
            ),
        )

        val viewModel = buildViewModel(preferencesRepository, publicationRepository)
        viewModel.onIntent(ReaderIntent.OpenPublication(publicationId))
        // 3d.5 — jamais advanceUntilIdle() ici : scheduleEyeRestReminder est
        // auto-récurrent (voir resumeFromEyeRestReminder), la file de délais
        // virtuels ne se vide donc jamais. runCurrent() suffit : l'ouverture
        // de publication ne suspend sur aucun delay() propre à elle.
        dispatcher.scheduler.runCurrent()

        assertFalse("pas de popup avant l'echeance", viewModel.state.value.isEyeRestReminderVisible)

        dispatcher.scheduler.advanceTimeBy(60_000L)
        dispatcher.scheduler.runCurrent()

        assertTrue("popup affiche a l'echeance (1 min)", viewModel.state.value.isEyeRestReminderVisible)
        assertEquals(EYE_REST_REMINDER_COUNTDOWN_S, viewModel.state.value.eyeRestReminderCountdownS)

        viewModel.onIntent(ReaderIntent.ResumeFromEyeRestReminder)
        // isEyeRestReminderVisible est remis à false SYNCHRONEMENT dans
        // resumeFromEyeRestReminder() (pas dans le launch{} de reprogrammation) :
        // aucune avance de dispatcher n'est même nécessaire pour l'observer.

        assertFalse("Reprendre referme le popup", viewModel.state.value.isEyeRestReminderVisible)

        // "Reprendre" reprogramme l'intervalle complet (comportement réel
        // et voulu, voir doc 3d.5) : un job récurrent reste donc en
        // attente. `runTest` termine par un drain implicite de la même
        // TestCoroutineScheduler que `Dispatchers.Main` (partagée via
        // `Dispatchers.setMain(dispatcher)`) — un job qui se reprogramme
        // indéfiniment ferait boucler ce drain implicite pour toujours,
        // même sans aucun advanceUntilIdle() explicite dans ce test.
        // Casser la chaîne avant la fin du test (comme le ferait un
        // ViewModel réel détruit par le framework, onCleared()) :
        viewModel.onIntent(ReaderIntent.SetEyeRestReminderEnabled(false))
        dispatcher.scheduler.runCurrent()

        // Le timer de checkpoint de session (Lot Sessions) est lui aussi
        // auto-récurrent et démarre inconditionnellement à l'ouverture
        // d'une publication, indépendamment du rappel de repos oculaire —
        // même raison de le casser explicitement ici.
        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun desactiver_le_rappel_empeche_le_popup_meme_apres_l_intervalle() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(eyeRestReminderEnabled = true, eyeRestReminderIntervalMinutes = 1))
        val publicationRepository = FakePublicationRepository()
        val publicationId = "pub-eyerest-2"
        publicationRepository.insert(
            Publication(
                id = publicationId, title = "Test", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 1,
                importDate = 0L,
            ),
        )

        val viewModel = buildViewModel(preferencesRepository, publicationRepository)
        viewModel.onIntent(ReaderIntent.OpenPublication(publicationId))
        dispatcher.scheduler.runCurrent()

        viewModel.onIntent(ReaderIntent.SetEyeRestReminderEnabled(false))
        dispatcher.scheduler.runCurrent()

        dispatcher.scheduler.advanceTimeBy(120_000L)
        dispatcher.scheduler.runCurrent()

        assertFalse(viewModel.state.value.isEyeRestReminderVisible)

        // Timer de checkpoint de session, auto-récurrent — même raison
        // que dans le test précédent.
        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }
}
