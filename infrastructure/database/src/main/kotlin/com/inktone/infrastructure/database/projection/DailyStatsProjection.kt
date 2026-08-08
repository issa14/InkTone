package com.inktone.infrastructure.database.projection

/**
 * Projection Room pour l'histogramme quotidien (Lot Statistiques Palier 1).
 *
 * Une ligne par jour, avec les durées visuelle et TTS séparées.
 * La date est au format ISO "YYYY-MM-DD".
 */
data class DailyStatsProjection(
    val date: String,
    val visualMs: Long,
    val ttsMs: Long,
)
