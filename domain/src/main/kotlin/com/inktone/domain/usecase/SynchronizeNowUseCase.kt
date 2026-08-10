package com.inktone.domain.usecase

import com.inktone.domain.service.SyncNowService
import com.inktone.domain.service.SyncOperationResult

/**
 * Point d'entrée unique pour "Synchroniser maintenant" (tâche 11.8),
 * qu'il soit déclenché manuellement (`feature/sync`) ou automatiquement
 * (`AutoSyncWorker`, `infrastructure/worker`) — les deux appellent cette
 * même façade, jamais [SyncNowService] directement, pour qu'un futur
 * changement de contrat n'ait qu'un seul appelant à mettre à jour.
 *
 * Pas de `@Inject` (domain reste pur Kotlin, Tâche 1.8) — fourni par
 * `UseCaseModule` (`data/di`).
 */
class SynchronizeNowUseCase(private val syncNowService: SyncNowService) {
    suspend operator fun invoke(): SyncOperationResult = syncNowService.synchronizeNow()
}
