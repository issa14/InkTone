package com.inktone.feature.onboarding

/**
 * Lot 10, Tâche 10.3 — l'onboarding redevient une pure présentation
 * (décision actée depuis la conception, jamais respectée jusqu'ici :
 * `CrashConsent`/`VoiceDownload` retirés). Trois cartes fixes, un seul
 * intent : terminer et poser l'indicateur persisté
 * (`UserPreferences.hasSeenOnboarding`).
 */
data class OnboardingUiState(val hasCompleted: Boolean = false)

sealed interface OnboardingIntent {
    /** Envoyé par « Passer » (cartes 1/2) et « Commencer » (carte 3) — les deux terminent l'onboarding. */
    data object Complete : OnboardingIntent
}
