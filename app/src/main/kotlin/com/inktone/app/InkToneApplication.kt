package com.inktone.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
