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
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.model.VoiceProfile
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * 3d.6, tests 1 et 2 — non-régression de l'antipattern « contrôle
 * décoratif » du curseur de vitesse (avant 3d.1 : `currentSpeed = 1.0f`
 * en dur, `onSpeedChange` vide). `SetTtsSpeed` doit écrire sur le profil
 * vocal actif (pas sur `UserPreferences`, voir doc du lot 3d tâche 3d.1)
 * et la valeur doit survivre à une réouverture de publication.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelVoiceSpeedTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun buildViewModel(
        preferencesRepository: FakePreferencesRepository = FakePreferencesRepository(),
        voiceProfileRepository: FakeVoiceProfileRepository = FakeVoiceProfileRepository(),
        publicationRepository: FakePublicationRepository = FakePublicationRepository(),
        readingStateRepository: FakeReadingStateRepository = FakeReadingStateRepository(),
    ): ReaderViewModel {
        val annotationRepository = FakeAnnotationRepository()
        val bookmarkRepository = FakeBookmarkRepository()
        return ReaderViewModel(
            ttsEngine = FakeTtsEngine(),
            audioSegmentPlayer = AudioSegmentPlayer(),
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
        )
    }

    private suspend fun insertTestPublication(repository: FakePublicationRepository, id: String) {
        repository.insert(
            Publication(
                id = id, title = "Test", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 1,
                importDate = 0L,
            ),
        )
    }

    @Test
    fun deplacer_le_curseur_ecrit_la_vitesse_sur_le_profil_actif_et_pas_sur_userPreferences() = runTest {
        // 3d.5 — le rappel de repos oculaire (activé par défaut, recurrent)
        // rendrait dispatcher.scheduler.advanceUntilIdle() non terminant ;
        // ce test ne porte pas sur le repos oculaire, on le désactive. Même
        // raison pour runCurrent() plutôt qu'advanceUntilIdle() ci-dessous :
        // le timer de checkpoint de session démarre lui aussi
        // inconditionnellement à l'ouverture d'une publication.
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(eyeRestReminderEnabled = false))
        val voiceProfileRepository = FakeVoiceProfileRepository()
        val publicationRepository = FakePublicationRepository()
        val publicationId = "pub-speed"
        insertTestPublication(publicationRepository, publicationId)

        val viewModel = buildViewModel(preferencesRepository, voiceProfileRepository, publicationRepository)
        viewModel.onIntent(ReaderIntent.OpenPublication(publicationId))
        dispatcher.scheduler.runCurrent()

        // Antipattern décoratif d'origine : le curseur affichait 1.0x en dur.
        assertEquals(1.0f, viewModel.state.value.activeVoiceProfile?.speed)

        viewModel.onIntent(ReaderIntent.SetTtsSpeed(1.8f))
        dispatcher.scheduler.runCurrent()

        assertEquals(1.8f, viewModel.state.value.activeVoiceProfile?.speed)
        // La vitesse appartient au profil de voix, jamais à UserPreferences
        // (doc du lot 3d, tâche 3d.1 : "ne pas ajouter de champ à
        // UserPreferences, la vitesse appartient au profil").
        val savedProfile = viewModel.state.value.activeVoiceProfile
        assertNotNull(savedProfile)
        assertEquals(savedProfile, voiceProfileRepository.getById(savedProfile!!.id))

        // Casse le timer de checkpoint (auto-récurrent) comme le ferait
        // onCleared() sur un vrai ViewModel détruit — sinon le drain
        // implicite de fin de runTest boucle indéfiniment.
        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun rouvrir_le_panneau_restitue_la_vitesse_persistee_pas_1_0x() = runTest {
        // 3d.5 — le rappel de repos oculaire (activé par défaut, recurrent)
        // rendrait dispatcher.scheduler.advanceUntilIdle() non terminant ;
        // ce test ne porte pas sur le repos oculaire, on le désactive. Même
        // raison pour runCurrent() plutôt qu'advanceUntilIdle() ci-dessous :
        // le timer de checkpoint de session démarre lui aussi
        // inconditionnellement à l'ouverture d'une publication.
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(eyeRestReminderEnabled = false))
        val voiceProfileRepository = FakeVoiceProfileRepository()
        val publicationRepository = FakePublicationRepository()
        val publicationId = "pub-speed-2"
        insertTestPublication(publicationRepository, publicationId)

        val viewModel = buildViewModel(preferencesRepository, voiceProfileRepository, publicationRepository)
        viewModel.onIntent(ReaderIntent.OpenPublication(publicationId))
        dispatcher.scheduler.runCurrent()
        viewModel.onIntent(ReaderIntent.SetTtsSpeed(2.4f))
        dispatcher.scheduler.runCurrent()

        // Simule une réouverture de publication (ex. relancer l'app) :
        // reconstruit un ViewModel à partir des MÊMES repositories.
        val reopened = buildViewModel(preferencesRepository, voiceProfileRepository, publicationRepository)
        reopened.onIntent(ReaderIntent.OpenPublication(publicationId))
        dispatcher.scheduler.runCurrent()

        assertEquals(2.4f, reopened.state.value.activeVoiceProfile?.speed)

        // Casse les timers de checkpoint (auto-récurrents) des deux
        // ViewModels comme le ferait onCleared() sur de vrais ViewModels
        // détruits — sinon le drain implicite de fin de runTest boucle
        // indéfiniment.
        viewModel.cancelCheckpointTimerForTest()
        reopened.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun selectionner_une_voix_met_a_jour_le_profil_actif_et_les_preferences() = runTest {
        // 3d.5 — le rappel de repos oculaire (activé par défaut, recurrent)
        // rendrait dispatcher.scheduler.advanceUntilIdle() non terminant ;
        // ce test ne porte pas sur le repos oculaire, on le désactive. Même
        // raison pour runCurrent() plutôt qu'advanceUntilIdle() ci-dessous :
        // le timer de checkpoint de session démarre lui aussi
        // inconditionnellement à l'ouverture d'une publication.
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(eyeRestReminderEnabled = false))
        val voiceProfileRepository = FakeVoiceProfileRepository()
        val otherProfile = VoiceProfile(id = "vp-other", engine = TtsEngineId.SHERPA_ONNX, voice = "ff_siwis", language = "fr-FR")
        voiceProfileRepository.save(otherProfile)
        val publicationRepository = FakePublicationRepository()
        val publicationId = "pub-speed-3"
        insertTestPublication(publicationRepository, publicationId)

        val viewModel = buildViewModel(preferencesRepository, voiceProfileRepository, publicationRepository)
        viewModel.onIntent(ReaderIntent.OpenPublication(publicationId))
        dispatcher.scheduler.runCurrent()

        viewModel.onIntent(ReaderIntent.SetActiveVoiceProfile(otherProfile.id))
        dispatcher.scheduler.runCurrent()

        assertEquals(otherProfile, viewModel.state.value.activeVoiceProfile)
        assertEquals(otherProfile.id, preferencesRepository.get().activeVoiceProfileId)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }
}
