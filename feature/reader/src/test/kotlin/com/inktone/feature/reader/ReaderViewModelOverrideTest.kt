package com.inktone.feature.reader

import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.core.testing.fake.FakeTtsSegmentCache
import com.inktone.core.testing.fake.FakePronunciationRuleRepository
import com.inktone.core.testing.fake.FakeAnnotationRepository
import com.inktone.core.testing.fake.FakeBookmarkRepository
import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeThemeRepository
import com.inktone.core.testing.fake.FakeTtsEngine
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.UserPreferences
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
import org.junit.Before
import org.junit.Test
import com.inktone.core.testing.fake.FakeChapterParser
import com.inktone.core.testing.fake.FakeEpubResourceResolver
import com.inktone.core.testing.fake.FakeVoiceProfileRepository

/**
 * Tache 8.2 — verifie EffectiveReadingSettings.resolve() (Tache 1.3) en
 * conditions reelles : un vrai reglage global (Tache 8.1) ET une vraie
 * surcharge par publication (ReaderIntent.SetOverrides, Tache 8.2) en
 * interaction, via le ReaderViewModel complet plutot qu'en isolation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelOverrideTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun surcharge_publication_prime_visiblement_sur_reglage_global() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val readingStateRepository = FakeReadingStateRepository()
        val publicationRepository = FakePublicationRepository()
        val bookmarkRepository = FakeBookmarkRepository()
        val annotationRepository = FakeAnnotationRepository()

        val publicationId = "pub-1"
        publicationRepository.insert(
            Publication(
                id = publicationId, title = "Test", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 1,
                importDate = 0L,
            ),
        )

        val viewModel = ReaderViewModel(
            ttsEngine = FakeTtsEngine(),
            playbackOrchestrator = PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(readingStateRepository), GetReadingStateUseCase(readingStateRepository), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository(), FakeTtsSegmentCache(), FakePronunciationRuleRepository()),
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
            voiceProfileRepository = com.inktone.core.testing.fake.FakeVoiceProfileRepository(),
            getVoiceProfiles = com.inktone.domain.usecase.GetVoiceProfilesUseCase(com.inktone.core.testing.fake.FakeVoiceProfileRepository()),
            readingSessionRepository = com.inktone.core.testing.fake.FakeReadingSessionRepository(),
            themeRepository = FakeThemeRepository(),
            fixedPageRenderer = com.inktone.core.testing.fake.FakeFixedPageRenderer(),
            chapterParser = FakeChapterParser(),
            epubResourceResolver = FakeEpubResourceResolver(),
            narrationSessionContinuation = NarrationSessionContinuation(
                PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository(), FakeTtsSegmentCache(), FakePronunciationRuleRepository()),
                FakeReadingSessionRepository(),
            ),
        )

        // 3d.5 — le rappel de repos oculaire (activé par défaut, recurrent)
        // rendrait dispatcher.scheduler.advanceUntilIdle() non terminant ;
        // ce test ne porte pas sur le repos oculaire, on le désactive.
        // Même raison pour utiliser runCurrent() plutôt qu'advanceUntilIdle()
        // ci-dessous : le timer de checkpoint de session démarre lui aussi
        // inconditionnellement à l'ouverture d'une publication.
        preferencesRepository.update(UserPreferences(theme = ReadingTheme.PAPIER_CLAIR.id, eyeRestReminderEnabled = false))
        viewModel.onIntent(ReaderIntent.OpenPublication(publicationId))
        dispatcher.scheduler.runCurrent()

        // Sans surcharge : le theme global (Papier Clair) s'applique.
        assertEquals(ReadingTheme.PAPIER_CLAIR.id, viewModel.state.value.effectiveSettings.theme)
        assertEquals(ReadingTheme.PAPIER_CLAIR, viewModel.state.value.resolvedTheme)

        viewModel.onIntent(ReaderIntent.SetOverrides(ReadingOverrides(theme = ReadingTheme.OBSIDIENNE.id)))
        dispatcher.scheduler.runCurrent()

        // La surcharge gagne, pas le reglage global.
        assertEquals(ReadingTheme.OBSIDIENNE.id, viewModel.state.value.effectiveSettings.theme)
        assertEquals(ReadingTheme.OBSIDIENNE, viewModel.state.value.resolvedTheme)
        assertEquals(ReadingTheme.OBSIDIENNE.id, readingStateRepository.get(publicationId)?.overrides?.theme)

        viewModel.onIntent(ReaderIntent.SetOverrides(null))
        dispatcher.scheduler.runCurrent()

        // La surcharge effacee : le reglage global reprend la main.
        assertEquals(ReadingTheme.PAPIER_CLAIR.id, viewModel.state.value.effectiveSettings.theme)

        // Casse le timer de checkpoint (auto-récurrent) comme le ferait
        // onCleared() sur un vrai ViewModel détruit — sinon le drain
        // implicite de fin de runTest boucle indéfiniment.
        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }
}
