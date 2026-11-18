package com.inktone.feature.onboarding

/**
 * Lot 10, Tâche 10.3 — l'onboarding redevient une pure présentation
 * (décision actée depuis la conception, jamais respectée jusqu'ici :
 * `CrashConsent`/`VoiceDownload` retirés). Trois cartes fixes, un seul
 * intent : terminer et poser l'indicateur persisté
 * (`UserPreferences.hasSeenOnboarding`).
 */
data class OnboardingUiState(
    val hasCompleted: Boolean = false,
    /** Terminé par « Importer un livre » : la Bibliothèque ouvre aussitôt le sélecteur. */
    val importFirstBook: Boolean = false,
)

sealed interface OnboardingIntent {
    /**
     * Termine l'onboarding : « Passer », « Plus tard », ou « Importer un livre »
     * avec [importFirstBook] (audit UI P1-15 : sans détour par une
     * bibliothèque vide qui redemande d'importer).
     */
    data class Complete(val importFirstBook: Boolean = false) : OnboardingIntent
}
