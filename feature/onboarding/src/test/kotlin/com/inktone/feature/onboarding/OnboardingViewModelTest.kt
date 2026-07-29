package com.inktone.feature.onboarding

import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakeVoiceModelDownloadService
import com.inktone.domain.service.VoiceDownloadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tache 8.8 — les trois chemins de l'onboarding. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun onboarding_accepte_le_crash_reporting() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val viewModel = OnboardingViewModel(preferencesRepository, FakeVoiceModelDownloadService())

        viewModel.onIntent(OnboardingIntent.Next) // Welcome -> CrashConsent
        viewModel.onIntent(OnboardingIntent.SetCrashReporting(true))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(preferencesRepository.get().crashReportingEnabled)
        assertEquals(OnboardingStep.VoiceDownload, viewModel.state.value.step)
    }

    @Test
    fun onboarding_refuse_le_crash_reporting() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val viewModel = OnboardingViewModel(preferencesRepository, FakeVoiceModelDownloadService())

        viewModel.onIntent(OnboardingIntent.Next) // Welcome -> CrashConsent
        viewModel.onIntent(OnboardingIntent.SetCrashReporting(false))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(!preferencesRepository.get().crashReportingEnabled)
        assertEquals(OnboardingStep.VoiceDownload, viewModel.state.value.step)
    }

    @Test
    fun onboarding_reporte_le_telechargement_de_voix_sans_bloquer() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val viewModel = OnboardingViewModel(preferencesRepository, FakeVoiceModelDownloadService())

        viewModel.onIntent(OnboardingIntent.Next) // Welcome -> CrashConsent
        viewModel.onIntent(OnboardingIntent.SetCrashReporting(false))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(OnboardingStep.VoiceDownload, viewModel.state.value.step)

        // Le Palier 1 (Android natif) doit rester utilisable sans
        // telecharger la voix (ADR-018) - "Passer" avance sans bloquer.
        viewModel.onIntent(OnboardingIntent.Next)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(OnboardingStep.Done, viewModel.state.value.step)
    }

    @Test
    fun onboarding_telecharge_la_voix_et_avance_une_fois_terminee() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val viewModel = OnboardingViewModel(
            preferencesRepository,
            FakeVoiceModelDownloadService(
                listOf(VoiceDownloadProgress.InProgress(50, 100), VoiceDownloadProgress.Complete),
            ),
        )

        viewModel.onIntent(OnboardingIntent.Next)
        viewModel.onIntent(OnboardingIntent.SetCrashReporting(false))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onIntent(OnboardingIntent.StartVoiceDownload)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(OnboardingStep.Done, viewModel.state.value.step)
    }
}
