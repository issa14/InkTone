package com.inktone.domain.usecase

import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.DailyReadingStats
import com.inktone.domain.model.HeatmapPoint
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingSessionRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class StatisticsUiState(
    val totalReadingTimeMs: Long = 0,
    val totalVisualMs: Long = 0,
    val totalTtsMs: Long = 0,
    val booksFinished: Int = 0,
    val currentStreakDays: Int = 0,
    val averageWpm: Int = 0,
    val maxStreakDays: Int = 0,
    val dailyGoalMinutes: Int = 20,
    val todayReadingMinutes: Long = 0,
    // Lot Statistiques Palier 1 — graphiques
    val dailyStats: List<DailyReadingStats> = emptyList(),
    val heatmapSlots: List<HeatmapSlot> = emptyList(),
)

/**
 * Créneau horaire normalisé pour la heatmap (Lot Statistiques Palier 1).
 * Les heures brutes 0-23 du DAO sont regroupées en 5 créneaux
 * conformément à la cible UX : 6h, 10h, 14h, 18h, 22h.
 *
 * [slotIndex] : 0=6h, 1=10h, 2=14h, 3=18h, 4=22h.
 * [dayOfWeek] : 0=Dimanche … 6=Samedi.
 * [intensity] : 0.0–1.0, normalisé sur le max du créneau.
 */
data class HeatmapSlot(
    val slotIndex: Int,
    val dayOfWeek: Int,
    val intensity: Float,
)

/**
 * Statistiques de lecture (Lot Statistiques Palier 1).
 *
 * Remplace le chargement mémoire (`getAll().sumOf {}`) par des
 * agrégations SQL natives. Seul le calcul WPM (30 dernières sessions
 * avec mots lus) utilise encore `getAll()` — les autres métriques
 * passent par les projections Room.
 */
class GetStatisticsUseCase(
    private val readingSessionRepository: ReadingSessionRepository,
    private val publicationRepository: PublicationRepository,
) {
    suspend operator fun invoke(): StatisticsUiState {
        // ───── KPIs globaux (SQL) ─────
        val totals = readingSessionRepository.getTotalStats()
        val finishedCount = publicationRepository.observeFiltered(FilterMode.READ).first().size

        // ───── Streaks (SQL : jours distincts) ─────
        val distinctDays = readingSessionRepository.getDistinctReadingDays()
        val streakDays = distinctDays.mapNotNull { parseDateToEpochDay(it) }
        val streak = computeStreak(streakDays)
        val maxStreak = computeMaxStreak(streakDays)

        // ───── WPM (30 dernières sessions avec mots, nécessite données individuelles) ─────
        val sessions = readingSessionRepository.getAll()
        val sessionsWithWords = sessions
            .filter { it.wordsRead > 0 && it.durationMs > 0 }
            .sortedByDescending { it.startedAt }
            .take(30)
        val averageWpm = if (sessionsWithWords.isNotEmpty()) {
            val totalWords = sessionsWithWords.sumOf { it.wordsRead }
            val totalMinutes = sessionsWithWords.sumOf { it.durationMs } / 60_000.0
            if (totalMinutes > 0) (totalWords / totalMinutes).toInt() else 0
        } else 0

        // ───── Lecture du jour ─────
        val todayStart = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        val todaySessions = sessions.filter {
            TimeUnit.MILLISECONDS.toDays(it.startedAt) == todayStart
        }
        val todayReadingMinutes = todaySessions.sumOf { it.durationMs } / 60_000L

        // ───── Histogramme : 30 derniers jours (SQL) ─────
        val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        val dailyStats = readingSessionRepository.getDailyStatsSince(thirtyDaysAgo)

        // ───── Heatmap : regroupement en 5 créneaux (SQL + Kotlin) ─────
        val heatmapRaw = readingSessionRepository.getHeatmapStatsSince(thirtyDaysAgo)
        val heatmapSlots = computeHeatmapSlots(heatmapRaw)

        return StatisticsUiState(
            totalReadingTimeMs = totals.totalVisualMs + totals.totalTtsMs,
            totalVisualMs = totals.totalVisualMs,
            totalTtsMs = totals.totalTtsMs,
            booksFinished = finishedCount,
            currentStreakDays = streak,
            averageWpm = averageWpm,
            maxStreakDays = maxStreak,
            todayReadingMinutes = todayReadingMinutes,
            dailyStats = dailyStats,
            heatmapSlots = heatmapSlots,
        )
    }

    // ───── Streak (sur des epochDays, pas des timestamps) ─────

    private fun computeStreak(epochDays: List<Long>): Int {
        if (epochDays.isEmpty()) return 0
        val days = epochDays.sortedDescending()
        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        if (days.first() != today && days.first() != today - 1) return 0
        var streak = 1
        var expected = days.first() - 1
        for (day in days.drop(1)) {
            if (day == expected) { streak++; expected-- }
            else if (day < expected) break
        }
        return streak
    }

    private fun computeMaxStreak(epochDays: List<Long>): Int {
        if (epochDays.isEmpty()) return 0
        val days = epochDays.distinct().sorted()
        var maxStreak = 1
        var currentStreak = 1
        for (i in 1 until days.size) {
            if (days[i] == days[i - 1] + 1) { currentStreak++; maxStreak = maxOf(maxStreak, currentStreak) }
            else currentStreak = 1
        }
        return maxStreak
    }

    // ───── Heatmap : heures brutes → 5 créneaux UX ─────

    /**
     * Regroupe les points de heatmap bruts (0-23h) dans les 5 créneaux
     * de la cible d'écran (6h, 10h, 14h, 18h, 22h) et normalise
     * l'intensité par créneau sur 0.0–1.0.
     */
    internal fun computeHeatmapSlots(raw: List<HeatmapPoint>): List<HeatmapSlot> {
        if (raw.isEmpty()) return emptyList()

        val slotCenters = listOf(6, 10, 14, 18, 22)
        data class SlotKey(val dayOfWeek: Int, val slotIndex: Int)

        // Regrouper les heures brutes dans les créneaux les plus proches
        val slotCounts = mutableMapOf<SlotKey, Int>()
        for (point in raw) {
            val slotIndex = slotCenters.indices.minByOrNull {
                kotlin.math.abs(point.hourOfDay - slotCenters[it])
            } ?: 2 // fallback : milieu de journée
            val key = SlotKey(point.dayOfWeek, slotIndex)
            slotCounts[key] = (slotCounts[key] ?: 0) + point.interactionCount
        }

        // Normaliser : chaque créneau (même slotIndex) a son propre max
        val maxBySlot = mutableMapOf<Int, Int>()
        for ((key, count) in slotCounts) {
            maxBySlot[key.slotIndex] = maxOf(maxBySlot[key.slotIndex] ?: 0, count)
        }

        return slotCounts.map { (key, count) ->
            val max = maxBySlot[key.slotIndex] ?: 1
            HeatmapSlot(
                slotIndex = key.slotIndex,
                dayOfWeek = key.dayOfWeek,
                intensity = (count.toFloat() / max).coerceIn(0f, 1f),
            )
        }
    }

    // ───── Helpers ─────

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    private fun parseDateToEpochDay(date: String): Long? {
        return try {
            TimeUnit.MILLISECONDS.toDays(dateFormat.parse(date)!!.time)
        } catch (_: Exception) {
            null
        }
    }
}
