package com.inktone.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.DailyReadingStats
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.service.ExportFormat
import com.inktone.domain.service.StatisticsExportService
import com.inktone.domain.usecase.ActivityChartState
import com.inktone.domain.usecase.CurrentBookState
import com.inktone.domain.usecase.GetCurrentBookUseCase
import com.inktone.domain.usecase.GetStatisticsUseCase
import com.inktone.domain.usecase.KpiState
import com.inktone.domain.usecase.StatisticsResult
import com.inktone.domain.usecase.StatisticsUiState
import com.inktone.domain.usecase.StatsPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * ViewModel du tableau de bord statistiques (Lot Statistiques Palier 2).
 *
 * Combine deux flux :
 * 1. Les statistiques brutes de [GetStatisticsUseCase].
 * 2. La publication en cours (dernière lue, via [ReadingSessionRepository]).
 *
 * Le formatage des durées est exécuté sur [Dispatchers.Default] pour
 * ne jamais bloquer le thread UI. L'état est exposé via [stateIn] avec
 * [SharingStarted.WhileSubscribed] (5s de rétention après le dernier
 * abonné) pour éviter de requêter la base si l'app passe en arrière-plan.
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    getStatistics: GetStatisticsUseCase,
    getCurrentBook: GetCurrentBookUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val exportService: StatisticsExportService,
) : ViewModel() {

    private val _effects = Channel<ExportEvent>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // Tache 7.4 — sélection Semaine/Mois de la carte "Activité", pilotée
    // par un intent explicite (onPeriodSelected), combinée dans l'état
    // unique du tableau de bord.
    private val selectedPeriod = MutableStateFlow(StatsPeriod.MONTH)

    val state: StateFlow<StatisticsUiState> = combine(
        getStatistics(),
        getCurrentBook(),
        preferencesRepository.observe(),
        selectedPeriod,
    ) { rawStats, book, prefs, period ->
        StatisticsUiState.Ready(
            kpi = rawStats.toKpiState(prefs.dailyGoalMinutes),
            activity = rawStats.toActivityState(period),
            currentBook = book?.withRemainingTime(rawStats.averageWpm),
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState.Loading)

    fun onPeriodSelected(period: StatsPeriod) {
        selectedPeriod.value = period
    }

    // ───── Formatage ─────

    private fun StatisticsResult.toKpiState(dailyGoalMinutes: Int) = KpiState(
        totalVisualTimeFormatted = formatDuration(totalVisualMs),
        totalTtsTimeFormatted = formatDuration(totalTtsMs),
        totalReadingTimeMs = totalVisualMs + totalTtsMs,
        booksFinished = booksFinished,
        currentStreakDays = currentStreakDays,
        maxStreakDays = maxStreakDays,
        averageWpm = averageWpm,
        todayReadingMinutes = todayReadingMinutes,
        todayReadingMinutesFormatted = "${todayReadingMinutes} min",
        dailyGoalMinutes = dailyGoalMinutes,
        totalWordsReadFormatted = formatAbbreviated(totalWordsRead),
        totalPagesReadFormatted = formatAbbreviated(totalWordsRead / WORDS_PER_PAGE_ESTIMATE),
        regularityLabel = regularityLabelFor(currentStreakDays),
    )

    private fun StatisticsResult.toActivityState(period: StatsPeriod): ActivityChartState {
        val windowDays = if (period == StatsPeriod.WEEK) DAYS_IN_WEEK else DAYS_IN_MONTH
        val windowedStats = dailyStats.takeLast(windowDays)
        val periodTotalMs = windowedStats.sumOf { it.visualMs + it.ttsMs }
        return ActivityChartState(
            dailyStats = windowedStats,
            variationPercent = computeVariation(dailyStats, windowDays),
            heatmapSlots = heatmapSlots,
            peakSlotIndex = peakSlotIndex,
            period = period,
            periodTotalFormatted = formatDuration(periodTotalMs),
        )
    }

    private fun formatDuration(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return "${hours}h ${minutes}m"
    }

    /**
     * Format abrégé K/M pour les grands nombres (Tache 7.2) — un compteur
     * de mots à sept chiffres est illisible brut. Ex. 1 250 000 → "1,3M".
     */
    private fun formatAbbreviated(value: Long): String = when {
        value >= 1_000_000L -> String.format(Locale.FRANCE, "%.1fM", value / 1_000_000.0)
        value >= 1_000L -> String.format(Locale.FRANCE, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }

    /**
     * Libellé de régularité de la carte objectif du jour (Tache 7.2),
     * dérivé de la série en cours — jamais un texte constant.
     */
    private fun regularityLabelFor(currentStreakDays: Int): String = when {
        currentStreakDays <= 0 -> "Pas de série en cours"
        currentStreakDays < 3 -> "Série en cours · régularité en construction"
        currentStreakDays < 7 -> "Série en cours · régularité modérée"
        else -> "Série en cours · régularité élevée"
    }

    /**
     * Calcule la variation en % de la période sélectionnée par rapport à
     * la période équivalente précédente (Tache 7.4 — semaine vs semaine
     * précédente, ou mois vs mois précédent). Retourne "+12%", "—" si pas
     * de période de comparaison, ou "0%" si identique.
     */
    private fun computeVariation(dailyStats: List<DailyReadingStats>, windowDays: Int): String {
        if (dailyStats.size < windowDays * 2) return "—"
        val current = dailyStats.takeLast(windowDays).sumOf { it.visualMs + it.ttsMs }
        val previous = dailyStats.dropLast(windowDays).takeLast(windowDays).sumOf { it.visualMs + it.ttsMs }
        if (previous == 0L) return "—"
        val pct = ((current - previous) * 100.0 / previous).toInt()
        return "${if (pct >= 0) "+" else ""}$pct%"
    }

    // ───── Livre en cours (formatage, pas de query) ─────

    /**
     * Estime le temps restant pour ce livre à partir du WPM moyen
     * et du temps déjà passé.
     *
     * Formule (cible UX) : temps restant ≈ tempsTotal / progression * (1 - progression).
     */
    private fun CurrentBookState.withRemainingTime(averageWpm: Int): CurrentBookState {
        if (progressPercent <= 0f || totalBookTimeMs <= 0L) return this
        val estimatedTotalMs = (totalBookTimeMs / progressPercent).toLong()
        val remainingMs = estimatedTotalMs - totalBookTimeMs
        return copy(remainingTimeFormatted = if (remainingMs > 0) formatDuration(remainingMs) else null)
    }

    // ───── Export ─────

    fun export(format: ExportFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = when (format) {
                    ExportFormat.CSV -> exportService.exportCsv()
                    ExportFormat.JSON -> exportService.exportJson()
                }
                _effects.send(ExportEvent.Share(file, format))
            } catch (e: Exception) {
                _effects.send(ExportEvent.Error(e.localizedMessage ?: "Erreur d'exportation"))
            }
        }
    }

    private companion object {
        const val DAYS_IN_WEEK = 7
        const val DAYS_IN_MONTH = 30

        // Tache 7.2 — "Pages lues" n'a pas de source de données propre : le
        // domaine bannit volontairement un champ pageCount générique (EPUB
        // reflowable, cf. Publication.kt). Estimation d'affichage seule,
        // jamais persistée, sur la base d'une moyenne éditoriale courante.
        const val WORDS_PER_PAGE_ESTIMATE = 250L
    }
}

sealed interface ExportEvent {
    data class Share(val file: File, val format: ExportFormat) : ExportEvent
    data class Error(val message: String) : ExportEvent
}