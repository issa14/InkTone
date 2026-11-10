package com.inktone.infrastructure.worker

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.inktone.domain.service.PreAnalysisScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation WorkManager de [PreAnalysisScheduler] : un travail unique
 * ([WORK_NAME_PREANALYSIS_REFRESH], politique `KEEP`) — une demande pendant
 * qu'une régénération est planifiée ou en cours ne la relance pas.
 *
 * Classe concrète injectée telle quelle par `InkToneApplication` pour le
 * déclenchement au démarrage (même raison que `CoverStorageMigration` :
 * `app` ne connaît pas les contrats du domaine, Blueprint §12.4).
 */
@Singleton
class WorkManagerPreAnalysisScheduler @Inject constructor(
    private val workManager: WorkManager,
) : PreAnalysisScheduler {

    override fun scheduleRefresh() {
        val request = OneTimeWorkRequestBuilder<PreAnalysisRefreshWorker>()
            // Attente d'un import en cours (voir PreAnalysisRefreshWorker).
            .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME_PREANALYSIS_REFRESH, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME_PREANALYSIS_REFRESH = "inktone_preanalysis_refresh"
        private const val RETRY_DELAY_SECONDS = 30L
    }
}
