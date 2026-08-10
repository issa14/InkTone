package com.inktone.domain.service

import com.inktone.domain.model.SyncProviderId

/** Un fichier distant du dossier applicatif de synchronisation. */
data class SyncRemoteFile(
    val name: String,
    val modifiedAt: Long,
    val sizeBytes: Long,
)

/**
 * Causes d'échec distinguées explicitement (tâche 11.5 — leçon du lot 5 :
 * ne pas réduire plusieurs causes à un `else`). Chaque implémentation
 * (`infrastructure`) doit mapper ses erreurs concrètes (HTTP 401, 403
 * quota, `IOException`, 404) vers l'une de ces causes plutôt que de
 * laisser fuir une exception non typée jusqu'à l'UI.
 */
enum class SyncFailureReason { INVALID_TOKEN, QUOTA_EXCEEDED, NETWORK, NOT_FOUND, UNKNOWN }

sealed interface SyncOperationResult {
    data object Success : SyncOperationResult
    data class Failed(val reason: SyncFailureReason, val message: String) : SyncOperationResult
}

/**
 * Contrat fournisseur de synchronisation (tâche 11.2) — téléverser,
 * télécharger, lister, supprimer, rien d'autre. Aucune implémentation
 * ne doit être visible depuis un module `feature` (même discipline que
 * [ImportScheduler]/[ImportProgressObserver]) : le réseau et les
 * identifiants restent dans `infrastructure`.
 *
 * **Aucun verrou distant disponible** (Google Drive n'en offre pas) :
 * la cohérence entre appareils repose sur l'horodatage par entité
 * ([com.inktone.domain.model.SyncAccount.lastSyncAt] et les timestamps
 * propres à chaque entité synchronisée) et le relire-avant-écrire du
 * registre de flotte (palier C), jamais sur une sémantique
 * d'écriture atomique du fournisseur.
 */
interface SyncProvider {
    val id: SyncProviderId

    suspend fun upload(fileName: String, bytes: ByteArray): SyncOperationResult
    suspend fun download(fileName: String): ByteArray?
    suspend fun list(): List<SyncRemoteFile>
    suspend fun delete(fileName: String): SyncOperationResult
}
