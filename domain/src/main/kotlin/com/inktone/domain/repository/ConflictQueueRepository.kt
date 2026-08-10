package com.inktone.domain.repository

import com.inktone.domain.model.PositionConflict

/**
 * File des conflits de position détectés, en attente d'arbitrage
 * (tâche 11.10). Persiste (Room) : un conflit détecté en arrière-plan
 * doit survivre jusqu'à la prochaine ouverture de l'app, y compris à
 * travers un redémarrage du processus.
 */
interface ConflictQueueRepository {
    suspend fun listPending(): List<PositionConflict>

    /** N'ajoute rien si un conflit est déjà en file pour `conflict.publicationId` — idempotent entre synchros répétées avant résolution. */
    suspend fun enqueue(conflict: PositionConflict)

    suspend fun remove(publicationId: String)
}
