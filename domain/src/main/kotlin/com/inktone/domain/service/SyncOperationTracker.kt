package com.inktone.domain.service

import kotlinx.coroutines.flow.Flow

/** Ce qui est en train de se passer *maintenant*, jamais persisté — redémarre à `NONE` à chaque lancement de l'app. */
enum class SyncOperation { NONE, AUTHENTICATING, SYNCING }

/**
 * Suivi en mémoire de l'opération de synchronisation en cours (tâche
 * 11.2). Sert de brique à [com.inktone.domain.usecase
 * .ObserveSyncUiStateUseCase] pour distinguer `Authenticating`/`Syncing`
 * de l'état persisté (compte lié ou non) — même rôle qu'[ImportProgressObserver]
 * pour l'import, mais côté écriture ici : le code qui pilote un
 * transfert (palier B/C) annonce son début/sa fin, l'UI observe.
 */
interface SyncOperationTracker {
    fun observe(): Flow<SyncOperation>
    suspend fun begin(operation: SyncOperation)
    suspend fun end()
}
