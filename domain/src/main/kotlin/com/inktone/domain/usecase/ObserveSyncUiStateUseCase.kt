package com.inktone.domain.usecase

import com.inktone.domain.model.SyncUiState
import com.inktone.domain.repository.SyncAccountRepository
import com.inktone.domain.service.SyncOperation
import com.inktone.domain.service.SyncOperationTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Assemble [SyncUiState] à partir de deux sources indépendantes (tâche
 * 11.2) : le compte persisté ([SyncAccountRepository], configuré ou
 * non) et l'opération en cours ([SyncOperationTracker], éphémère,
 * jamais persistée). Aucun module `feature` ne doit recombiner ces deux
 * sources lui-même — ce use case est le seul endroit qui connaît la
 * règle de priorité (authentification en cours prime sur tout, un
 * transfert ne peut avoir lieu que sur un compte déjà configuré).
 *
 * Pas de `@Inject` (domain reste pur Kotlin, Tâche 1.8) — fourni par
 * `UseCaseModule` (`data/di`).
 */
class ObserveSyncUiStateUseCase(
    private val syncAccountRepository: SyncAccountRepository,
    private val syncOperationTracker: SyncOperationTracker,
) {
    operator fun invoke(): Flow<SyncUiState> =
        combine(syncAccountRepository.observe(), syncOperationTracker.observe()) { account, operation ->
            when {
                operation == SyncOperation.AUTHENTICATING -> SyncUiState.Authenticating
                account == null -> SyncUiState.Unconfigured
                operation == SyncOperation.SYNCING -> SyncUiState.Syncing(account)
                else -> SyncUiState.Configured(account)
            }
        }
}
