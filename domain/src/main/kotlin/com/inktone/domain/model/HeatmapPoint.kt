package com.inktone.domain.model

/**
 * Point de heatmap d'habitudes de lecture (Lot Statistiques Palier 1).
 *
 * [dayOfWeek] : 0 = Dimanche … 6 = Samedi.
 * [hourOfDay] : 0–23.
 * [durationMs] : temps de lecture (visuelle + écoute) des sessions
 *   commencées dans ce créneau. Une durée et non un nombre de sessions :
 *   le nombre de fragments mesure les interruptions (un fragment toutes
 *   les 5 minutes, un de plus à chaque mise en arrière-plan), pas le temps
 *   passé à lire.
 */
data class HeatmapPoint(
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val durationMs: Long,
)
