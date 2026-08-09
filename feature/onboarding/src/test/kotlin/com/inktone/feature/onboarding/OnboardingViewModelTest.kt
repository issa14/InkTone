package com.inktone.feature.onboarding

import com.inktone.core.testing.fake.FakePreferencesRepository
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

/**
 * Lot 10, Tâche 10.7 — l'onboarding est redevenu une pure présentation
 * (Tâche 10.3) : `OnboardingIntent` ne porte plus que `Complete`, envoyé
 * aussi bien par « Passer » (cartes 1/2) que par « Commencer » (carte 3)
 * dans `OnboardingScreen`. `SetCrashReporting`/`StartVoiceDownload`
 * n'existent plus — cette absence est déjà prouvée par la compilation de
 * ce fichier, pas seulement par ces tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun l_etat_initial_n_est_pas_termine() = runTest {
        val viewModel = OnboardingViewModel(FakePreferencesRepository())
        assertEquals(false, viewModel.state.value.hasCompleted)
    }

    @Test
    fun complete_pose_l_indicateur_persiste_et_marque_l_etat_termine() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        assertTrue(!preferencesRepository.get().hasSeenOnboarding)
        val viewModel = OnboardingViewModel(preferencesRepository)

        viewModel.onIntent(OnboardingIntent.Complete)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(preferencesRepository.get().hasSeenOnboarding)
        assertTrue(viewModel.state.value.hasCompleted)
    }

    @Test
    fun complete_ne_touche_a_aucune_autre_preference() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val before = preferencesRepository.get()
        val viewModel = OnboardingViewModel(preferencesRepository)

        viewModel.onIntent(OnboardingIntent.Complete)
        dispatcher.scheduler.advanceUntilIdle()

        val after = preferencesRepository.get()
        assertEquals(before.crashReportingEnabled, after.crashReportingEnabled)
        assertEquals(before.theme, after.theme)
    }
}
