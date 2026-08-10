package com.inktone.infrastructure.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.inktone.domain.repository.SyncAccountRepository
import com.inktone.domain.service.SyncOperationResult
import com.inktone.domain.usecase.SynchronizeNowUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Synchronisation automatique en arrière-plan (tâche 11.8). Ne fait
 * **jamais** de signal de présence périodique — un déclenchement qui ne
 * trouve aucun compte configuré (désinscription entre-temps) ne touche
 * rien de distant et rend simplement `success()` : la dernière activité
 * de la flotte ne se met à jour qu'à l'occasion d'une synchronisation
 * réelle ([SynchronizeNowUseCase], via [com.inktone.data.sync
 * .SyncNowManager]), jamais par ce Worker lui-même.
 *
 * [WorkManagerSyncScheduler] fixe les contraintes réseau (Wi-Fi
 * uniquement ou non) à l'enfilage — ce Worker n'a pas à les revérifier.
 */
@HiltWorker
class AutoSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncAccountRepository: SyncAccountRepository,
    private val synchronizeNow: SynchronizeNowUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (syncAccountRepository.get() == null) return Result.success()

        return when (synchronizeNow()) {
            is SyncOperationResult.Success -> Result.success()
            is SyncOperationResult.Failed -> Result.retry()
        }
    }
}
