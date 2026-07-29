package com.inktone.domain.service

import kotlinx.coroutines.flow.Flow

/**
 * Observe la progression de l'import en cours (Tâche 6.8) — abstraction
 * du domaine sur WorkManager (infrastructure/worker), même raison que
 * [ImportScheduler] (Tâche 6.2bis) : `feature/library` ne doit jamais
 * dépendre de WorkManager directement (Blueprint §12.4).
 */
interface ImportProgressObserver {
    fun observe(): Flow<ImportProgress>
}

/**
 * `total == 0 && !hasQueuedChunks` : aucun import en cours, la bannière
 * doit rester cachée.
 *
 * Limitation documentée (pas cachée) : quand `WorkManagerImportScheduler`
 * (Tâche 6.2bis) découpe un import en plusieurs `WorkRequest` chaînées
 * (> 50 URI), `current`/`total` reflètent uniquement le lot en cours
 * d'exécution, pas un total agrégé sur toute la chaîne — WorkManager
 * n'expose pas la `Data` d'entrée d'un travail pas encore démarré
 * (`ENQUEUED`/`BLOCKED`), seulement sa `Data` de progression une fois
 * `RUNNING`. `hasQueuedChunks` signale au moins que d'autres lots
 * suivent, sans pouvoir en donner la taille avant leur démarrage.
 */
data class ImportProgress(
    val current: Int = 0,
    val total: Int = 0,
    val hasQueuedChunks: Boolean = false,
)
