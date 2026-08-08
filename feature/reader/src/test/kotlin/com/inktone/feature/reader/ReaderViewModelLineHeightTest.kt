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
import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingTheme
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
import org.junit.Before
import org.junit.Test

/**
 * 3d.6, test 4 (moitié ViewModel — la moitié "styleKey pur" est déjà
 * couverte par `ChapterPaginationStateTest`, posée en 3b.2/3b.7) :
 * `ReaderUiState.lineHeightMultiplier` doit suivre `UserPreferences`
 * (réglage global, même patron que `isReadingRulerEnabled`), et
 * `SetOverrides` (thème) ne doit JAMAIS le faire varier — le thème est
 * délibérément absent de `PaginationStyleKey` (voir
 * `VirtualPagination.kt`), c'est exactement ce que ce test vérifie côté
 * état exposé par le ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelLineHeightTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun lineHeightMultiplier_suit_setLineHeight_mais_pas_un_changement_de_theme() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val readingStateRepository = FakeReadingStateRepository()
        val publicationRepository = FakePublicationRepository()
        val bookmarkRepository = FakeBookmarkRepository()
        val annotationRepository = FakeAnnotationRepository()
        val voiceProfileRepository = FakeVoiceProfileRepository()

        val viewModel = ReaderViewModel(
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
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1.4f, viewModel.state.value.lineHeightMultiplier)

        viewModel.onIntent(ReaderIntent.SetLineHeight(1.8f))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1.8f, viewModel.state.value.lineHeightMultiplier)
        assertEquals(1.8f, preferencesRepository.get().lineHeightMultiplier)

        // Changer le thème (surcharge par publication) ne doit JAMAIS
        // faire varier l'interligne — pas de couplage accidentel.
        viewModel.onIntent(ReaderIntent.SetOverrides(ReadingOverrides(theme = ReadingTheme.DARK)))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1.8f, viewModel.state.value.lineHeightMultiplier)

        // Reflète aussi un changement externe des préférences (même
        // patron d'observation continue que isReadingRulerEnabled).
        preferencesRepository.update(UserPreferences(lineHeightMultiplier = 1.2f))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1.2f, viewModel.state.value.lineHeightMultiplier)
    }
}
