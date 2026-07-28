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
 * **Limite non résolue par ce Worker, à la charge de l'appelant** :
 * `WorkManager` sérialise `Data` sur ~10 Ko max. Un `StringArray` d'URI
 * SAF (`content://…`, ~100-150 caractères chacune) dépasse cette limite
 * autour de ~70-90 URI — bien en-dessous des 500 EPUB du budget §11.2.
 * Aucun code de cette Phase n'enqueue encore de `WorkRequest` (dépend de
 * l'écran d'import, Tâche 6.6/6.8) : au moment où ce code sera écrit, il
 * doit découper `uris` en plusieurs `WorkRequest` chaînées plutôt que de
 * construire un seul `Data` géant — ne pas supposer qu'un seul
 * `ImportWorker` suffit pour un import de bibliothèque complète.
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
