package com.inktone.domain.model

/**
 * Point de heatmap d'habitudes de lecture (Lot Statistiques Palier 1).
 *
 * [dayOfWeek] : 0 = Dimanche … 6 = Samedi.
 * [hourOfDay] : 0–23.
 * [interactionCount] : nombre de sessions dans ce créneau.
 */
data class HeatmapPoint(
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val interactionCount: Int,
)
