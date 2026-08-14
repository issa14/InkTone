package com.inktone.domain.usecase

import com.inktone.domain.model.OpdsItem
import com.inktone.domain.service.OpdsDownloadScheduler
import com.inktone.domain.service.OpdsFailureReason

/** Résultat de la demande de téléchargement d'un livre OPDS (Lot 13, tâche 13.3.1). */
sealed interface DownloadOpdsBookResult {
    data class Scheduled(val workId: String) : DownloadOpdsBookResult
    data class Failure(val reason: OpdsFailureReason, val message: String) : DownloadOpdsBookResult
}

/**
 * Demande le téléchargement d'un livre OPDS (Lot 13, ADR-023) — ne
 * renvoie jamais le fichier, seulement un identifiant de travail
 * WorkManager (UX non bloquante). Vérifie `mimeType`/`acquisitionHref`
 * **avant** de lancer quoi que ce soit : lien indirect ou MIME non-EPUB
 * → [OpdsFailureReason.NON_DOWNLOADABLE_ACQUISITION], pas de tentative de
 * téléchargement vouée à l'échec.
 */
class DownloadOpdsBookUseCase(
    private val scheduler: OpdsDownloadScheduler,
) {
    suspend operator fun invoke(item: OpdsItem.Book, catalogId: String?): DownloadOpdsBookResult {
        if (item.acquisitionHref.isBlank()) {
            return DownloadOpdsBookResult.Failure(
                OpdsFailureReason.NON_DOWNLOADABLE_ACQUISITION,
                "Ce livre n'a pas de lien de téléchargement direct.",
            )
        }
        if (item.mimeType.isNotBlank() && !item.mimeType.contains("epub", ignoreCase = true)) {
            return DownloadOpdsBookResult.Failure(
                OpdsFailureReason.NON_DOWNLOADABLE_ACQUISITION,
                "Format non supporté : ${item.mimeType}",
            )
        }
        val workId = scheduler.enqueue(item.acquisitionHref, catalogId, item.title)
        return DownloadOpdsBookResult.Scheduled(workId)
    }
}
