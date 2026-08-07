package com.inktone.domain.service

import com.inktone.domain.usecase.ImportResult

/**
 * Stockage persistant des résultats d'import (Palier A, Lot 5).
 *
 * Abstraction du domaine sur Room — même discipline que
 * [ImportScheduler] et [ImportProgressObserver] (Blueprint §12.4) :
 * `feature/library` ne doit jamais dépendre de Room directement.
 *
 * Chaque session d'import est identifiée par un [sessionId] unique
 * (UUID), généré par l'appelant ([ImportScheduler]). Les résultats
 * sont consultables après la fin du worker, y compris après un
 * redémarrage du processus.
 */
interface ImportResultsStore {
    /**
     * Démarre une nouvelle session d'import — supprime les résultats
     * des sessions précédentes pour ne conserver que la session active.
     */
    suspend fun beginSession(sessionId: String)

    /**
     * Enregistre le résultat d'un fichier traité dans la session [sessionId].
     */
    suspend fun recordResult(sessionId: String, fileName: String, result: ImportResult)

    /**
     * Retourne tous les résultats de la session [sessionId], triés
     * par type (échecs en premier) puis par nom de fichier.
     */
    suspend fun getResults(sessionId: String): List<ImportResultEntry>

    /**
     * Supprime les résultats de la session [sessionId] après
     * consultation ou après un délai.
     */
    suspend fun clearSession(sessionId: String)
}

/**
 * Résultat d'import d'un fichier, prêt à afficher.
 *
 * [message] est non-null pour [ImportResult.Corrupted] et
 * [ImportResult.DrmProtected]. [existingPublicationId] est non-null
 * pour [ImportResult.Duplicate].
 */
data class ImportResultEntry(
    val fileName: String,
    val resultType: String, // "success", "duplicate", "drm_protected", "corrupted", "unsupported_format"
    val message: String? = null,
    val existingPublicationId: String? = null,
)
