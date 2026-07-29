package com.inktone.infrastructure.worker

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.inktone.domain.service.ImportScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémente [ImportScheduler] via WorkManager (Tâche 6.2bis) — nom de
 * travail unique (`WORK_NAME_IMPORT`) avec `APPEND_OR_REPLACE` : plusieurs
 * déclenchements successifs s'enchaînent plutôt que de s'écraser, et la
 * bannière de progression (Tâche 6.8) peut observer ce seul nom stable
 * quel que soit le nombre de déclenchements.
 *
 * Découpe [fileUris] en lots de [MAX_URIS_PER_CHUNK] chaînés
 * (`WorkContinuation.then`) — résout la limite documentée dans
 * `ImportWorker` : `Data` sérialise sur ~10 Ko max, dépassé autour de
 * ~70-90 URI SAF, bien en-dessous des 500 EPUB du budget §11.2.
 */
@Singleton
class WorkManagerImportScheduler @Inject constructor(
    private val workManager: WorkManager,
) : ImportScheduler {

    override fun enqueue(fileUris: List<String>) {
        if (fileUris.isEmpty()) return

        val requests = fileUris.chunked(MAX_URIS_PER_CHUNK).map { chunk ->
            OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(workDataOf(ImportWorker.KEY_URIS to chunk.toTypedArray()))
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .build()
        }

        var continuation = workManager.beginUniqueWork(WORK_NAME_IMPORT, ExistingWorkPolicy.APPEND_OR_REPLACE, requests.first())
        for (index in 1 until requests.size) {
            continuation = continuation.then(requests[index])
        }
        continuation.enqueue()
    }

    companion object {
        const val WORK_NAME_IMPORT = "inktone_import"
        private const val MAX_URIS_PER_CHUNK = 50
    }
}
