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
    val booksFinished: Int,
    val currentStreakDays: Int,
    val maxStreakDays: Int,
    /**
     * Vitesse de lecture en mots par minute, mesurée sur la seule lecture
     * visuelle. `0` tant qu'aucune session visuelle n'a été enregistrée —
     * l'écran affiche alors un tiret plutôt qu'un zéro, qui se lirait comme
     * une mesure.
     */
    val averageWpm: Int,
    val todayReadingMinutes: Long,
    val dailyGoalMinutes: Int,
    /** Libellé de régularité de la carte objectif du jour (Tache 7.2). */
    val regularityLabel: String,
)

/**
 * Sélecteur de période pour la carte "Activité" (Tache 7.4).
 */
enum class StatsPeriod { WEEK, MONTH }

/**
 * Section 2 — Graphiques d'Activité.
 *
 * [variationPercent] : "+12%" en cas de hausse par rapport à la
 * période équivalente précédente (calculée sur [period]), "—" si
 * aucune variation calculable. [periodTotalFormatted] est le total de
 * la période sélectionnée (ex. "6h 25m").
 */
data class ActivityChartState(
    val dailyStats: List<DailyReadingStats>,
    val variationPercent: String,
    val heatmapSlots: List<HeatmapSlot>,
    val peakSlotIndex: Int?,
    val period: StatsPeriod,
    val periodTotalFormatted: String,
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
