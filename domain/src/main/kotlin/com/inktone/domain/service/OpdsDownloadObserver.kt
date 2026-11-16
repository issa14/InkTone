package com.inktone.domain.service

import kotlinx.coroutines.flow.Flow

/**
 * Événement de fin de téléchargement OPDS (Lot 13, tâche 13.3.4).
 * [publicationId] est non-nul uniquement sur succès — c'est ce qui
 * permet au canal d'effet MVI d'afficher « Lire maintenant ». [failureCause] est la cause d'un
 * échec, en français, à afficher après le titre (audit UX D64) ; null si le succès ou la cause est inconnue.
 */
data class OpdsDownloadEvent(
    val bookTitle: String,
    val publicationId: String?,
    val success: Boolean,
    val failureCause: String? = null,
)

/**
 * Observe la fin des téléchargements OPDS (Lot 13, ADR-023) — abstraction
 * du domaine sur le pont worker → UI : `OpdsDownloadWorker` publie
 * ([publish]), `OpdsViewModel` observe ([observe]). L'implémentation est
 * un bus en mémoire (même processus que le worker).
 */
interface OpdsDownloadObserver {
    fun observe(): Flow<OpdsDownloadEvent>
    suspend fun publish(event: OpdsDownloadEvent)
}
