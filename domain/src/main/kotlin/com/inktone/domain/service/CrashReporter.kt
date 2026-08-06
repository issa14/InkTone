package com.inktone.domain.service

/**
 * Rapport de crash opt-in (ADR-014, Blueprint §13.4 K10). Le domaine ne
 * connaît ni Firebase ni Android — deux implémentations existent côté
 * `infrastructure/crashreporting` (Firebase Crashlytics, no-op), le choix
 * entre les deux se fait dans le module `app` (seul endroit qui sait si
 * `google-services.json` était présent au moment du build).
 *
 * `setCollectionEnabled` reflète en continu
 * `UserPreferences.crashReportingEnabled` — jamais activé tant que
 * l'utilisateur n'a pas consenti (opt-in explicite, jamais opt-out).
 */
interface CrashReporter {
    fun setCollectionEnabled(enabled: Boolean)
    fun recordException(throwable: Throwable)
    fun log(message: String)
}
