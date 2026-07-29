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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Import en tâche de fond (Tâche 6.2) — l'import survit à la mise en
 * arrière-plan de l'app, obligatoire pour plusieurs centaines de
 * fichiers (Blueprint §11.2 : 500 EPUB ≤ 5 min, personne ne garde l'app
 * au premier plan tout ce temps).
 *
 * Parallélisé (K2, Tâche 6.3) — borné à [MAX_CONCURRENT_IMPORTS] permits,
 * après confirmation que WAL (Tâche 2.3) absorbe l'écriture concurrente
 * (K1 avant K2, même discipline que le legacy a apprise à ses dépens).
 * **Gain non mesuré ici** : la comparaison séquentiel/parallèle est la
 * Tâche 6.9 (benchmark), pas supposée avant d'être vérifiée — si l'import
 * s'avère I/O-bound plutôt que CPU-bound, le gain réel pourrait être
 * marginal malgré la complexité ajoutée.
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

    override suspend fun doWork(): Result = coroutineScope {
        val uris = inputData.getStringArray(KEY_URIS) ?: return@coroutineScope Result.failure()

        // Compteurs partages entre coroutines concurrentes (Dispatchers.Default,
        // plusieurs threads reels) - AtomicInteger, pas de var+= non protege.
        val successCount = AtomicInteger(0)
        val duplicateCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val completedCount = AtomicInteger(0)

        val semaphore = Semaphore(permits = MAX_CONCURRENT_IMPORTS)
        uris.map { uri ->
            async {
                semaphore.withPermit {
                    // Chaque appel importPublication() -> publicationParser.parse()
                    // ouvre le ZIP UNE FOIS (ReadiumPublicationParser, Tache 3.2 -
                    // AssetRetriever.retrieve() une seule fois par appel, pas de
                    // reouverture repetee comme le legacy le faisait, K2).
                    when (importPublication(uri)) {
                        is ImportResult.Success -> successCount.incrementAndGet()
                        is ImportResult.Duplicate -> duplicateCount.incrementAndGet()
                        else -> failureCount.incrementAndGet()
                    }
                    setProgressAsync(
                        Data.Builder()
                            .putInt(KEY_PROGRESS_CURRENT, completedCount.incrementAndGet())
                            .putInt(KEY_PROGRESS_TOTAL, uris.size)
                            .build(),
                    )
                }
            }
        }.awaitAll()

        val output = Data.Builder()
            .putInt(KEY_RESULT_SUCCESS, successCount.get())
            .putInt(KEY_RESULT_DUPLICATE, duplicateCount.get())
            .putInt(KEY_RESULT_FAILURE, failureCount.get())
            .build()
        Result.success(output)
    }

    companion object {
        const val KEY_URIS = "uris"
        const val KEY_PROGRESS_CURRENT = "progress_current"
        const val KEY_PROGRESS_TOTAL = "progress_total"
        const val KEY_RESULT_SUCCESS = "result_success"
        const val KEY_RESULT_DUPLICATE = "result_duplicate"
        const val KEY_RESULT_FAILURE = "result_failure"
        private const val MAX_CONCURRENT_IMPORTS = 4
    }
}
