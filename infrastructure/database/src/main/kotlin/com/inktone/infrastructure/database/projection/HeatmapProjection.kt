package com.inktone.infrastructure.database.projection

/**
 * Projection Room pour la heatmap d'habitudes de lecture
 * (Lot Statistiques Palier 1).
 *
 * [dayOfWeek] : 0 = Dimanche … 6 = Samedi (convention SQLite strftime('%w')).
 * [hourOfDay] : 0–23 (convention SQLite strftime('%H')).
 * [interactionCount] : nombre de sessions démarrées dans ce créneau.
 */
data class HeatmapProjection(
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val interactionCount: Int,
)
