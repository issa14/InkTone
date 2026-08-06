package com.inktone.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.inktone.infrastructure.crashreporting.CrashReportingConsentObserver
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/**
 * Point d'entrée Hilt — assemble le graphe de DI (`data`, `infrastructure`,
 * Tâche 2.6). `Configuration.Provider` (Tâche 6.2) fournit `HiltWorkerFactory`
 * à WorkManager pour qu'`ImportWorker` (`@HiltWorker`) reçoive ses
 * dépendances par injection plutôt que par un constructeur réflexif —
 * l'initialisation par défaut de WorkManager (androidx-startup) est
 * désactivée dans le manifest en conséquence (voir AndroidManifest.xml).
 */
@HiltAndroidApp
class InkToneApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    // K10/ADR-014 — `app` n'a pas le droit de dépendre de `domain`
    // directement (Blueprint §12.4, checkArchitectureRules) : la logique
    // d'observation de `UserPreferences.crashReportingEnabled` (qui
    // référence CrashReporter/PreferencesRepository, des types domain)
    // vit dans `infrastructure/crashreporting`
    // (CrashReportingConsentObserver), `app` ne fait que déclencher [start].
    @Inject lateinit var crashReportingConsentObserver: CrashReportingConsentObserver

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()

        // Observation continue, pas un chargement ponctuel au démarrage :
        // un changement du toggle dans les Réglages
        // (SettingsIntent.SetCrashReportingEnabled) doit prendre effet
        // immédiatement, sans redémarrage de l'app. La méta-donnée
        // manifest (firebase_crashlytics_collection_enabled=false)
        // couvre la fenêtre avant ce onCreate ; cette collecte reste
        // désactivée par défaut avec NoOpCrashReporter (le cas sans
        // google-services.json) et n'a alors aucun effet réel.
        crashReportingConsentObserver.start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}
