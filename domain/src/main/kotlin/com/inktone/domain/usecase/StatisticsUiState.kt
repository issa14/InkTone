package com.inktone.domain.usecase

import com.inktone.domain.model.DailyReadingStats

/**
 * État du tableau de bord statistiques (Lot Statistiques Palier 2).
 *
 * UDF strict : un seul état immuable par écran. Le ViewModel le produit
 * via `combine()` entre les stats brutes et la publication en cours,
 * avec `stateIn(WhileSubscribed(5000))`.
 */
sealed interface StatisticsUiState {
    data object Loading : StatisticsUiState
    data class Ready(
        val kpi: KpiState,
        val activity: ActivityChartState,
        val currentBook: CurrentBookState?,
    ) : StatisticsUiState
}

/**
 * Section 1 — KPIs & Objectifs.
 *
 * Les durées sont déjà formatées par le ViewModel sur Dispatchers.Default
 * (ex: "14h 32m"). Le composable UI reçoit des Strings prêtes à afficher.
 */
data class KpiState(
    val totalVisualTimeFormatted: String,
    val totalTtsTimeFormatted: String,
    val totalReadingTimeMs: Long,
    val booksFinished: Int,
    val currentStreakDays: Int,
    val maxStreakDays: Int,
    val averageWpm: Int,
    val todayReadingMinutes: Long,
    val todayReadingMinutesFormatted: String,
    val dailyGoalMinutes: Int,
)

/**
 * Section 2 — Graphiques d'Activité.
 *
 * [variationPercent] : "+12%" en cas de hausse par rapport à la
 * période précédente, "—" si aucune variation calculable.
 */
data class ActivityChartState(
    val dailyStats: List<DailyReadingStats>,
    val variationPercent: String,
    val heatmapSlots: List<HeatmapSlot>,
)

/**
 * Section 3 — Carte résumé du Livre en cours.
 *
 * Peut être null s'il n'y a aucune session en base (pas de livre en cours).
 */
data class CurrentBookState(
    val id: String,
    val title: String,
    val coverUri: String?,
    val progressPercent: Float,
    val totalBookTimeMs: Long = 0,
    val remainingTimeFormatted: String?,
)
