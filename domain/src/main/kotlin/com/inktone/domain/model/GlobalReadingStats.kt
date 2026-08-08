package com.inktone.domain.model

/**
 * KPIs globaux de lecture (Lot Statistiques Palier 1).
 * Agrégation SQL native — aucun chargement en mémoire.
 */
data class GlobalReadingStats(
    val totalVisualMs: Long,
    val totalTtsMs: Long,
    val booksInteracted: Int,
    val totalWordsRead: Long,
)
