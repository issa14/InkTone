package com.inktone.domain.repository

import com.inktone.domain.model.SyncAccount
import kotlinx.coroutines.flow.Flow

/**
 * Compte de synchronisation persisté (tâche 11.2) — `observe()` émet
 * `null` tant qu'aucun compte n'est lié (état `Unconfigured`).
 * `save()` remplace tout compte existant : c'est ce qui matérialise
 * l'exclusivité mutuelle entre fournisseurs, pas une contrainte
 * appliquée ailleurs.
 */
interface SyncAccountRepository {
    fun observe(): Flow<SyncAccount?>
    suspend fun get(): SyncAccount?
    suspend fun save(account: SyncAccount)
    suspend fun clear()

    /** Met à jour `lastSyncAt` et efface `lastAutoSyncFailed` — un succès efface un échec précédent. */
    suspend fun markSyncSucceeded(at: Long)

    /** Pose `lastAutoSyncFailed = true`, sans toucher à `lastSyncAt` — pilote la bannière persistante du Dashboard (palier C). */
    suspend fun markSyncFailed()
}
