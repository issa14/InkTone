package com.inktone.domain.service

/**
 * Déclenche le téléchargement d'un livre OPDS en tâche de fond (Lot 13,
 * tâche 13.3.2) — abstraction du domaine sur WorkManager
 * (`infrastructure:worker`), même discipline que [ImportScheduler] :
 * `feature:opds` ne dépend jamais de WorkManager directement
 * (Blueprint §12.4). Retourne l'identifiant de travail, utilisable pour
 * annuler via `WorkManager.cancelWorkById`.
 */
interface OpdsDownloadScheduler {
    fun enqueue(acquisitionHref: String, catalogId: String?, bookTitle: String): String
}
