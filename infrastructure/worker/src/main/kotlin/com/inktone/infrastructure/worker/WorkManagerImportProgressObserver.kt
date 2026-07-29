package com.inktone.infrastructure.worker

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.inktone.domain.service.ImportProgress
import com.inktone.domain.service.ImportProgressObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémente [ImportProgressObserver] via
 * `WorkManager.getWorkInfosForUniqueWorkFlow` (Tâche 6.8) — pas
 * `getWorkInfoByIdFlow` (suppose un seul ID connu à l'avance) : le nom
 * unique convient mieux ici puisque `WorkManagerImportScheduler` peut
 * enchaîner plusieurs `WorkRequest` (Tâche 6.2bis, > 50 URI) et que
 * plusieurs déclenchements successifs s'accumulent sur le même nom
 * (`APPEND_OR_REPLACE`).
 */
@Singleton
class WorkManagerImportProgressObserver @Inject constructor(
    private val workManager: WorkManager,
) : ImportProgressObserver {

    override fun observe(): Flow<ImportProgress> =
        workManager.getWorkInfosForUniqueWorkFlow(WorkManagerImportScheduler.WORK_NAME_IMPORT).map { infos ->
            val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
            val hasQueued = infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }

            if (running == null) {
                ImportProgress(hasQueuedChunks = hasQueued)
            } else {
                ImportProgress(
                    current = running.progress.getInt(ImportWorker.KEY_PROGRESS_CURRENT, 0),
                    total = running.progress.getInt(ImportWorker.KEY_PROGRESS_TOTAL, 0),
                    hasQueuedChunks = hasQueued,
                )
            }
        }
}
