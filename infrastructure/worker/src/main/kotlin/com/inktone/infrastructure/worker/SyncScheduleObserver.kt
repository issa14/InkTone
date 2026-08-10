package com.inktone.infrastructure.worker

import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.service.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reflète en continu `UserPreferences.syncAutoEnabled`/`syncWifiOnly` sur
 * [SyncScheduler] (tâche 11.8) — même patron que
 * `CrashReportingConsentObserver` (`infrastructure/crashreporting`) :
 * `app` ne peut pas nommer `PreferencesRepository`/`SyncScheduler`
 * (types `domain`) directement, cette classe vit dans `infrastructure`
 * (autorisé à dépendre de `domain`), `app` (`InkToneApplication
 * .onCreate`) se contente d'appeler [start].
 *
 * Une bascule des Réglages prend effet immédiatement (planifie ou
 * annule), sans redémarrage de l'app.
 */
@Singleton
class SyncScheduleObserver @Inject constructor(
    private val syncScheduler: SyncScheduler,
    private val preferencesRepository: PreferencesRepository,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            preferencesRepository.observe()
                .map { it.syncAutoEnabled to it.syncWifiOnly }
                .distinctUntilChanged()
                .collect { (autoEnabled, wifiOnly) ->
                    if (autoEnabled) syncScheduler.schedule(wifiOnly) else syncScheduler.cancel()
                }
        }
    }
}
