package com.inktone.infrastructure.worker

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Déclenchement au démarrage de la régénération des pré-analyses absentes
 * ou périmées (voir [PreAnalysisRefreshWorker]).
 *
 * Point d'entrée dédié plutôt que [WorkManagerPreAnalysisScheduler]
 * injecté directement : ce dernier implémente un contrat du domaine, que
 * `app` n'a pas le droit de voir, même comme supertype (Blueprint §12.4 —
 * même raison que `CoverStorageMigration`).
 */
@Singleton
class PreAnalysisRefreshOnStartup @Inject constructor(
    private val scheduler: WorkManagerPreAnalysisScheduler,
) {
    fun start() = scheduler.scheduleRefresh()
}
