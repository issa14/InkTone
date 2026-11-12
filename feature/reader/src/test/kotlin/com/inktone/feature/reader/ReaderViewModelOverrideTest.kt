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
import org.junit.Assert.assertNull
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
            renderedPageCache = com.inktone.core.testing.fake.FakeRenderedPageCache(),
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

    private fun buildViewModel(
        preferencesRepository: FakePreferencesRepository,
        readingStateRepository: FakeReadingStateRepository,
        publicationRepository: FakePublicationRepository,
    ): ReaderViewModel {
        val bookmarkRepository = FakeBookmarkRepository()
        val annotationRepository = FakeAnnotationRepository()
        return ReaderViewModel(
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
            renderedPageCache = com.inktone.core.testing.fake.FakeRenderedPageCache(),
            chapterParser = FakeChapterParser(),
            epubResourceResolver = FakeEpubResourceResolver(),
            narrationSessionContinuation = NarrationSessionContinuation(
                PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository(), FakeTtsSegmentCache(), FakePronunciationRuleRepository()),
                FakeReadingSessionRepository(),
            ),
        )
    }

    private suspend fun FakePublicationRepository.insertBook(id: String) = insert(
        Publication(
            id = id, title = "Test", format = PublicationFormat.EPUB,
            fileUri = "content://$id", fileHash = "hash-$id", fileSize = 10, chapterCount = 1,
            importDate = 0L,
        ),
    )

    /**
     * Bug réel corrigé (audit UX A8) : le curseur de taille du Lecteur
     * écrivait toujours une surcharge du livre ; chaque nouveau livre
     * repartait en 18 sp.
     */
    @Test
    fun sans_reglage_propre_au_livre_la_taille_choisie_vaut_pour_les_autres_livres() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(fontSize = 18))
        val readingStateRepository = FakeReadingStateRepository()
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insertBook("livre-a")
        publicationRepository.insertBook("livre-b")

        val first = buildViewModel(preferencesRepository, readingStateRepository, publicationRepository)
        first.onIntent(ReaderIntent.OpenPublication("livre-a"))
        dispatcher.scheduler.runCurrent()
        first.onIntent(ReaderIntent.SetFontSize(22))
        dispatcher.scheduler.runCurrent()

        assertEquals(22, first.state.value.effectiveSettings.fontSize)
        assertEquals("réglage général", 22, preferencesRepository.get().fontSize)
        assertNull("aucune surcharge implicite", readingStateRepository.get("livre-a")?.overrides)

        val second = buildViewModel(preferencesRepository, readingStateRepository, publicationRepository)
        second.onIntent(ReaderIntent.OpenPublication("livre-b"))
        dispatcher.scheduler.runCurrent()
        assertEquals(22, second.state.value.effectiveSettings.fontSize)

        first.cancelCheckpointTimerForTest()
        second.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun pour_ce_livre_uniquement_fige_taille_et_theme_sans_toucher_aux_reglages_generaux() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(fontSize = 18, theme = ReadingTheme.PAPIER_CLAIR.id))
        val readingStateRepository = FakeReadingStateRepository()
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insertBook("livre-a")

        val viewModel = buildViewModel(preferencesRepository, readingStateRepository, publicationRepository)
        viewModel.onIntent(ReaderIntent.OpenPublication("livre-a"))
        dispatcher.scheduler.runCurrent()

        viewModel.onIntent(ReaderIntent.SetBookSpecificSettings(true))
        dispatcher.scheduler.runCurrent()
        assertEquals(
            "les valeurs affichées sont figées pour ce livre",
            ReadingOverrides(theme = ReadingTheme.PAPIER_CLAIR.id, fontSize = 18),
            readingStateRepository.get("livre-a")?.overrides,
        )

        viewModel.onIntent(ReaderIntent.SetFontSize(26))
        dispatcher.scheduler.runCurrent()
        viewModel.onIntent(ReaderIntent.SetReadingTheme(ReadingTheme.OBSIDIENNE.id))
        dispatcher.scheduler.runCurrent()

        assertEquals(26, viewModel.state.value.effectiveSettings.fontSize)
        assertEquals(ReadingTheme.OBSIDIENNE, viewModel.state.value.resolvedTheme)
        assertEquals("réglages généraux intacts", 18, preferencesRepository.get().fontSize)
        assertEquals(ReadingTheme.PAPIER_CLAIR.id, preferencesRepository.get().theme)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun desactiver_pour_ce_livre_uniquement_revient_aux_reglages_generaux() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(fontSize = 20, theme = ReadingTheme.PAPIER_CLAIR.id))
        val readingStateRepository = FakeReadingStateRepository()
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insertBook("livre-a")

        val viewModel = buildViewModel(preferencesRepository, readingStateRepository, publicationRepository)
        viewModel.onIntent(ReaderIntent.OpenPublication("livre-a"))
        dispatcher.scheduler.runCurrent()
        viewModel.onIntent(ReaderIntent.SetOverrides(ReadingOverrides(theme = ReadingTheme.OBSIDIENNE.id, fontSize = 28)))
        dispatcher.scheduler.runCurrent()

        viewModel.onIntent(ReaderIntent.SetBookSpecificSettings(false))
        dispatcher.scheduler.runCurrent()

        assertNull(readingStateRepository.get("livre-a")?.overrides)
        assertEquals(20, viewModel.state.value.effectiveSettings.fontSize)
        assertEquals(ReadingTheme.PAPIER_CLAIR, viewModel.state.value.resolvedTheme)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }
}
