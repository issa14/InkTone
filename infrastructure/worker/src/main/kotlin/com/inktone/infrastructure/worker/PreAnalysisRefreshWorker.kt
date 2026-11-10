package com.inktone.infrastructure.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.inktone.domain.usecase.RefreshStalePreAnalysesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Régénère en tâche de fond les pré-analyses absentes ou périmées
 * ([RefreshStalePreAnalysesUseCase]).
 *
 * Jamais pendant un import : un livre en cours d'import est déjà en base
 * mais n'a pas encore écrit sa pré-analyse — la régénérer en même temps
 * referait le même travail en double. Le worker se replanifie alors
 * (`Result.retry`, backoff du [WorkManagerPreAnalysisScheduler]) jusqu'à la
 * fin de la chaîne d'import. Il n'est volontairement PAS ajouté à cette
 * chaîne : la bannière de progression d'import l'observe, et y
 * compterait une régénération comme un import.
 */
@HiltWorker
class PreAnalysisRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val refreshStalePreAnalyses: RefreshStalePreAnalysesUseCase,
    private val workManager: WorkManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val importInProgress = workManager
            .getWorkInfosForUniqueWorkFlow(WorkManagerImportScheduler.WORK_NAME_IMPORT)
            .first()
            .any { !it.state.isFinished }
        if (importInProgress) return Result.retry()

        val outcome = refreshStalePreAnalyses()
        return Result.success(
            workDataOf(
                KEY_REFRESHED to outcome.refreshed,
                KEY_ALREADY_FRESH to outcome.alreadyFresh,
                KEY_FAILED to outcome.failed,
            ),
        )
    }

    companion object {
        // Diagnostic uniquement (sortie `Data`, non consommée par l'UI).
        const val KEY_REFRESHED = "refreshed"
        const val KEY_ALREADY_FRESH = "already_fresh"
        const val KEY_FAILED = "failed"
    }
}
