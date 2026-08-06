package com.inktone.infrastructure.crashreporting

import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.service.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reflète en continu `UserPreferences.crashReportingEnabled` sur
 * `CrashReporter.setCollectionEnabled` (K10/ADR-014) — un changement du
 * toggle dans les Réglages prend effet immédiatement, sans redémarrage.
 * Vit dans `infrastructure` (autorisé à dépendre de `domain`) plutôt que
 * dans `app`, qui ne peut pas nommer `CrashReporter`/`PreferencesRepository`
 * (types `domain`) directement — `app` (`InkToneApplication.onCreate`) se
 * contente d'appeler [start] sur cette classe, sans jamais importer de
 * type `domain` lui-même.
 */
@Singleton
class CrashReportingConsentObserver @Inject constructor(
    private val crashReporter: CrashReporter,
    private val preferencesRepository: PreferencesRepository,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            preferencesRepository.observe().collect { preferences ->
                crashReporter.setCollectionEnabled(preferences.crashReportingEnabled)
            }
        }
    }
}
