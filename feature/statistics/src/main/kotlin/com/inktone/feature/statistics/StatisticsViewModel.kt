package com.inktone.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.core.designsystem.formatDurationMinutes
import com.inktone.core.designsystem.formatPercent
import com.inktone.domain.model.estimatedRemainingReadingMs
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
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _effects = Channel<ExportEvent>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // Tache 7.4 — sélection Semaine/Mois de la carte "Activité", pilotée
    // par un intent explicite (onPeriodSelected), combinée dans l'état
    // unique du tableau de bord.
    private val selectedPeriod = MutableStateFlow(StatsPeriod.WEEK)

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
        booksFinished = booksFinished,
        currentStreakDays = currentStreakDays,
        maxStreakDays = maxStreakDays,
        averageWpm = averageWpm,
        todayReadingMinutes = todayReadingMinutes,
        dailyGoalMinutes = dailyGoalMinutes,
        regularityLabel = regularityLabelFor(currentStreakDays, maxStreakDays),
    )

    private fun StatisticsResult.toActivityState(period: StatsPeriod): ActivityChartState {
        val windowDays = if (period == StatsPeriod.WEEK) DAYS_IN_WEEK else DAYS_IN_MONTH
        // `dailyStats` est creuse : `getDailyStatsSince` groupe uniquement les
        // jours ayant une session (GROUP BY date), un jour sans activité n'a
        // aucune ligne. Sans densification, les barres de l'histogramme
        // s'enchaîneraient sans tenir compte des jours vides, désalignant
        // visuellement l'axe des jours — et rendant le marqueur "jour
        // courant" (dernière barre) faux dès que le jour courant n'a encore
        // aucune session.
        val windowedStats = fillMissingDays(dailyStats, windowDays)
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

    /**
     * Complète la série creuse par des jours à zéro pour obtenir exactement
     * [windowDays] entrées consécutives se terminant aujourd'hui — la somme
     * totale est inchangée (un jour manquant ne contribue de toute façon que
     * pour 0), seul l'alignement visuel des barres en dépend.
     */
    private fun fillMissingDays(dailyStats: List<DailyReadingStats>, windowDays: Int): List<DailyReadingStats> {
        val byDate = dailyStats.associateBy { it.date }
        val today = LocalDate.now(clock)
        return (windowDays - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
            byDate[date] ?: DailyReadingStats(date = date, visualMs = 0L, ttsMs = 0L)
        }
    }

    private fun formatDuration(ms: Long): String = formatDurationMinutes(TimeUnit.MILLISECONDS.toMinutes(ms))

    /**
     * Libellé de régularité de la carte objectif du jour (Tache 7.2),
     * dérivé de la série en cours — jamais un texte constant.
     */
    private fun regularityLabelFor(currentStreakDays: Int, maxStreakDays: Int): String {
        if (currentStreakDays <= 0) return "Lisez aujourd'hui pour commencer une série"
        if (currentStreakDays >= maxStreakDays) return "Votre meilleure série"
        val toBeat = maxStreakDays - currentStreakDays + 1
        return if (toBeat > 1) "Encore $toBeat jours pour battre votre record" else "Encore 1 jour pour battre votre record"
    }

    /**
     * Calcule la variation en % de la période sélectionnée par rapport à
     * la période équivalente précédente (Tache 7.4 — semaine vs semaine
     * précédente, ou mois vs mois précédent). Retourne "+12%", "—" si pas
     * de période de comparaison, ou "0%" si identique.
     */
    private fun computeVariation(dailyStats: List<DailyReadingStats>, windowDays: Int): String {
        val dense = fillMissingDays(dailyStats, windowDays * 2)
        val current = dense.takeLast(windowDays).sumOf { it.visualMs + it.ttsMs }
        val previous = dense.dropLast(windowDays).takeLast(windowDays).sumOf { it.visualMs + it.ttsMs }
        if (previous == 0L) return "—"
        val pct = ((current - previous) * 100.0 / previous).toInt()
        return "${if (pct >= 0) "+" else ""}${formatPercent(pct)}"
    }

    // ───── Livre en cours (formatage, pas de query) ─────

    /** Temps restant estimé — règle et seuils dans `estimatedRemainingReadingMs`. */
    private fun CurrentBookState.withRemainingTime(averageWpm: Int): CurrentBookState {
        val remainingMs = estimatedRemainingReadingMs(totalBookTimeMs, progressPercent, totalChars, averageWpm)
        return copy(remainingTimeFormatted = remainingMs?.let(::formatDuration))
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
                _effects.send(ExportEvent.Error("L'export n'a pas pu être créé."))
            }
        }
    }

    private companion object {
        const val DAYS_IN_WEEK = 7
        const val DAYS_IN_MONTH = 30
    }
}

sealed interface ExportEvent {
    data class Share(val file: File, val format: ExportFormat) : ExportEvent
    data class Error(val message: String) : ExportEvent
}