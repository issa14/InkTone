package com.inktone.domain.model

import com.inktone.core.testing.fake.FakePreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tache 9.2.3 — trou de couverture identifie par l'audit de securite :
 * aucun test n'affirmait explicitement le defaut ADR-014 (opt-in, jamais
 * opt-out). Le seul test existant (OnboardingViewModelTest) verifie que
 * SetCrashReporting(true/false) fonctionne, jamais la valeur initiale
 * sur une installation fraiche.
 */
class UserPreferencesTest {

    @Test
    fun crashReportingEnabled_est_desactive_par_defaut() {
        assertFalse(UserPreferences().crashReportingEnabled)
    }

    @Test
    fun le_crash_reporting_est_desactive_par_defaut_sur_une_installation_fraiche() = runTest {
        // Pas de fixture, pas d'onboarding complete - etat vraiment initial.
        val preferencesRepository = FakePreferencesRepository()
        val prefs = preferencesRepository.get()
        assertFalse(prefs.crashReportingEnabled) // ADR-014 : opt-in, jamais opt-out
    }
}
