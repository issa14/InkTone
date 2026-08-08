package com.inktone.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.service.ExportFormat
import com.inktone.domain.service.StatisticsExportService
import com.inktone.domain.model.Publication
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingSessionRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.usecase.ActivityChartState
import com.inktone.domain.usecase.CurrentBookState
import com.inktone.domain.usecase.GetStatisticsUseCase
import com.inktone.domain.usecase.KpiState
import com.inktone.domain.usecase.StatisticsResult
import com.inktone.domain.usecase.StatisticsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    private val getStatistics: GetStatisticsUseCase,
    private val readingSessionRepository: ReadingSessionRepository,
    private val publicationRepository: PublicationRepository,
    private val readingStateRepository: ReadingStateRepository,
    private val exportService: StatisticsExportService,
) : ViewModel() {

    private val _effects = Channel<ExportEvent>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    val state: StateFlow<StatisticsUiState> = combine(
        flow { emit(getStatistics()) },
        flow { emit(currentBook()) },
    ) { rawStats, book ->
        withContext(Dispatchers.Default) {
            StatisticsUiState.Ready(
                kpi = rawStats.toKpiState(),
                activity = rawStats.toActivityState(),
                currentBook = book?.withRemainingTime(rawStats.averageWpm),
            )
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState.Loading)

    // ───── Formatage ─────

    private fun StatisticsResult.toKpiState() = KpiState(
        totalVisualTimeFormatted = formatDuration(totalVisualMs),
        totalTtsTimeFormatted = formatDuration(totalTtsMs),
        totalReadingTimeMs = totalVisualMs + totalTtsMs,
        booksFinished = booksFinished,
        currentStreakDays = currentStreakDays,
        maxStreakDays = maxStreakDays,
        averageWpm = averageWpm,
        todayReadingMinutes = todayReadingMinutes,
        todayReadingMinutesFormatted = "${todayReadingMinutes} min",
        dailyGoalMinutes = 20,
    )

    private fun StatisticsResult.toActivityState() = ActivityChartState(
        dailyStats = dailyStats,
        variationPercent = computeVariation(dailyStats),
        heatmapSlots = heatmapSlots,
    )

    private fun formatDuration(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return "${hours}h ${minutes}m"
    }

    /**
     * Calcule la variation en % de la semaine en cours par rapport à la
     * semaine précédente. Retourne "+12%", "—" si pas de période de
     * comparaison, ou "0%" si identique.
     */
    private fun computeVariation(dailyStats: List<com.inktone.domain.model.DailyReadingStats>): String {
        if (dailyStats.size < 14) return "—"
        val thisWeek = dailyStats.takeLast(7).sumOf { it.visualMs + it.ttsMs }
        val lastWeek = dailyStats.dropLast(7).takeLast(7).sumOf { it.visualMs + it.ttsMs }
        if (lastWeek == 0L) return "—"
        val pct = ((thisWeek - lastWeek) * 100.0 / lastWeek).toInt()
        return "${if (pct >= 0) "+" else ""}$pct%"
    }

    // ───── Livre en cours ─────

    private suspend fun currentBook(): CurrentBookState? {
        val publicationId = readingSessionRepository.getLastReadPublicationId() ?: return null
        val publication = publicationRepository.getById(publicationId) ?: return null
        val readingState = readingStateRepository.get(publicationId)

        val progressPercent = if (readingState != null && publication.chapterCount > 0) {
            // chapterIndex 0-based → dernier chapitre = chapterCount-1 → +1 pour 100%
            ((readingState.locator.chapterIndex.toFloat() + 1f) / publication.chapterCount).coerceIn(0f, 1f)
        } else 0f

        // Temps total passé sur ce livre pour l'estimation du temps restant
        val sessions = readingSessionRepository.getByPublicationId(publicationId)
        val totalBookTimeMs = sessions.sumOf { it.durationMs }

        return CurrentBookState(
            id = publication.id,
            title = publication.title,
            coverUri = publication.coverUri,
            progressPercent = progressPercent,
            totalBookTimeMs = totalBookTimeMs,
            remainingTimeFormatted = null, // rempli par withRemainingTime
        )
    }

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
            val file = when (format) {
                ExportFormat.CSV -> exportService.exportCsv()
                ExportFormat.JSON -> exportService.exportJson()
            }
            _effects.send(ExportEvent.Share(file, format))
        }
    }
}

sealed interface ExportEvent {
    data class Share(val file: File, val format: ExportFormat) : ExportEvent
}