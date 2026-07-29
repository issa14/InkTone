package com.inktone.infrastructure.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.inktone.domain.usecase.ImportPublicationUseCase
import com.inktone.domain.usecase.ImportResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Import en tâche de fond (Tâche 6.2) — l'import survit à la mise en
 * arrière-plan de l'app, obligatoire pour plusieurs centaines de
 * fichiers (Blueprint §11.2 : 500 EPUB ≤ 5 min, personne ne garde l'app
 * au premier plan tout ce temps).
 *
 * Séquentiel ici — la parallélisation (K2, Tâche 6.3) est délibérément
 * une étape séparée, après confirmation que WAL (déjà actif depuis la
 * Phase 2) absorbe la charge concurrente.
 *
 * **Limite `Data` de WorkManager (~10 Ko), à la charge de l'appelant** :
 * un seul `ImportWorker` ne suffit pas pour un import de bibliothèque
 * complète — un `StringArray` d'URI SAF (`content://…`, ~100-150
 * caractères chacune) dépasse ~10 Ko autour de ~70-90 URI, bien en-dessous
 * des 500 EPUB du budget §11.2. [WorkManagerImportScheduler] (Tâche
 * 6.2bis) découpe en plusieurs `WorkRequest` chaînées plutôt que de
 * construire un seul `Data` géant — ne jamais enqueue un `ImportWorker`
 * directement avec une liste non bornée, passer par ce scheduler.
 */
@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val importPublication: ImportPublicationUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uris = inputData.getStringArray(KEY_URIS) ?: return Result.failure()

        var successCount = 0
        var duplicateCount = 0
        var failureCount = 0

        uris.forEachIndexed { index, uri ->
            setProgressAsync(
                Data.Builder()
                    .putInt(KEY_PROGRESS_CURRENT, index + 1)
                    .putInt(KEY_PROGRESS_TOTAL, uris.size)
                    .build(),
            )

            when (importPublication(uri)) {
                is ImportResult.Success -> successCount++
                is ImportResult.Duplicate -> duplicateCount++
                else -> failureCount++
            }
        }

        val output = Data.Builder()
            .putInt(KEY_RESULT_SUCCESS, successCount)
            .putInt(KEY_RESULT_DUPLICATE, duplicateCount)
            .putInt(KEY_RESULT_FAILURE, failureCount)
            .build()
        return Result.success(output)
    }

    companion object {
        const val KEY_URIS = "uris"
        const val KEY_PROGRESS_CURRENT = "progress_current"
        const val KEY_PROGRESS_TOTAL = "progress_total"
        const val KEY_RESULT_SUCCESS = "result_success"
        const val KEY_RESULT_DUPLICATE = "result_duplicate"
        const val KEY_RESULT_FAILURE = "result_failure"
    }
}
