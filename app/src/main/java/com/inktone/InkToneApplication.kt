package com.inktone

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class InkToneApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        PerfLogger.init(this)
        PerfLogger.markAppStart()
        CrashReporter.init(this)
    }

    // Requis pour qu'un CoroutineWorker @HiltWorker (voir EpubImportWorker, PLAN import EPUB
    // §4) reçoive ses dépendances par injection Hilt plutôt que par un WorkerFactory par défaut
    // qui ne sait construire que des Worker à constructeur sans argument.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
