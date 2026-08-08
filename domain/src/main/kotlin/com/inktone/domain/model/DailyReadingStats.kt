package com.inktone.domain.model

/**
 * Statistiques de lecture pour un jour donné (Lot Statistiques Palier 1).
 *
 * [date] au format ISO "YYYY-MM-DD".
 */
data class DailyReadingStats(
    val date: String,
    val visualMs: Long,
    val ttsMs: Long,
)
