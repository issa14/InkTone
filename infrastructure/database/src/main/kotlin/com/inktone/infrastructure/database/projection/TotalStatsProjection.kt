package com.inktone.infrastructure.database.projection

/**
 * Projection Room pour les KPIs globaux de lecture (Lot Statistiques Palier 1).
 *
 * Agrégation SQL native — aucun chargement en mémoire.
 */
data class TotalStatsProjection(
    val totalVisualMs: Long,
    val totalTtsMs: Long,
    val booksInteracted: Int,
)
