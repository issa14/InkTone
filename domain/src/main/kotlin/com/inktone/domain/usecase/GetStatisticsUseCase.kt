package com.inktone.domain.usecase

import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.HeatmapPoint
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Résultat brut du UseCase statistiques (Palier 2) — toutes les valeurs
 * sont au format brut (Long, Int, List). Le formatage en Strings
 * ("14h 32m", "+12%") est fait par le ViewModel sur Dispatchers.Default.
 */
data class StatisticsResult(
    val totalVisualMs: Long,
    val totalTtsMs: Long,
    val booksFinished: Int,
    val currentStreakDays: Int,
    val averageWpm: Int,
    val maxStreakDays: Int,
    val todayReadingMinutes: Long,
    val totalWordsRead: Long,
    val dailyStats: List<com.inktone.domain.model.DailyReadingStats>,
    val heatmapSlots: List<HeatmapSlot>,
)

/**
 * Créneau horaire normalisé pour la heatmap (Lot Statistiques Palier 1).
 */
data class HeatmapSlot(
    val slotIndex: Int,
    val dayOfWeek: Int,
    val intensity: Float,
)

/**
 * Statistiques de lecture (Lot Statistiques Palier 1).
 *
 * Audit fix : plus aucun `getAll()` — toutes les métriques passent
 * par des requêtes SQL ciblées (LIMIT 30 pour le WPM, SUM pour
 * aujourd'hui, COUNT pour les livres finis). Les 5 appels DAO
 * indépendants sont lancés en parallèle via `coroutineScope`.
 */
class GetStatisticsUseCase(
    private val readingSessionRepository: ReadingSessionRepository,
    private val publicationRepository: PublicationRepository,
) {
    /** Retourne un Flow pour intégration directe dans `combine()` — pas de `flow { emit() }` wrapper dans le ViewModel. */
    operator fun invoke(): Flow<StatisticsResult> = flow {
        val result = coroutineScope {
            val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            // Tache 7.4 — 60 jours pour permettre la comparaison mois courant / mois
            // precedent (30 vs 30) en plus de la comparaison semaine (7 vs 7).
            val sixtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60)
            val totalsDeferred = async { readingSessionRepository.getTotalStats() }
            val finishedDeferred = async { publicationRepository.countFiltered(FilterMode.READ) }
            val distinctDaysDeferred = async { readingSessionRepository.getDistinctReadingDays() }
            val dailyStatsDeferred = async { readingSessionRepository.getDailyStatsSince(sixtyDaysAgo) }
            val heatmapDeferred = async { readingSessionRepository.getHeatmapStatsSince(thirtyDaysAgo) }

            val totals = totalsDeferred.await()
            val finishedCount = finishedDeferred.await()
            val distinctDays = distinctDaysDeferred.await()
            val dailyStats = dailyStatsDeferred.await()
            val heatmapRaw = heatmapDeferred.await()

            val streakDays = distinctDays.mapNotNull { parseDateToEpochDay(it) }
            val streak = computeStreak(streakDays)
            val maxStreak = computeMaxStreak(streakDays)

            val sessionsWithWords = readingSessionRepository.getRecentSessionsWithWords(30)
            val averageWpm = if (sessionsWithWords.isNotEmpty()) {
                val totalWords = sessionsWithWords.sumOf { it.wordsRead }
                val totalMinutes = sessionsWithWords.sumOf { it.durationMs } / 60_000.0
                if (totalMinutes > 0) (totalWords / totalMinutes).toInt() else 0
            } else 0

            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                .format(System.currentTimeMillis())
            val todayReadingMinutes = dailyStats
                .firstOrNull { it.date == todayKey }
                ?.let { (it.visualMs + it.ttsMs) / 60_000L } ?: 0L

            val heatmapSlots = computeHeatmapSlots(heatmapRaw)

            StatisticsResult(
                totalVisualMs = totals.totalVisualMs,
                totalTtsMs = totals.totalTtsMs,
                booksFinished = finishedCount,
                currentStreakDays = streak,
                averageWpm = averageWpm,
                maxStreakDays = maxStreak,
                todayReadingMinutes = todayReadingMinutes,
                totalWordsRead = totals.totalWordsRead,
                dailyStats = dailyStats,
                heatmapSlots = heatmapSlots,
            )
        }
        emit(result)
    }.flowOn(Dispatchers.Default)

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
     *
     * Les heures 0–3 sont rattachées au créneau 22h du jour précédent
     * (la nuit de lundi 2h appartient conceptuellement à la soirée de
     * dimanche) — sans cela, une session à 1h du matin tomberait dans
     * le créneau « matin ».
     */
    internal fun computeHeatmapSlots(raw: List<HeatmapPoint>): List<HeatmapSlot> {
        if (raw.isEmpty()) return emptyList()

        data class SlotKey(val dayOfWeek: Int, val slotIndex: Int)

        // Regrouper les heures brutes dans les créneaux les plus proches
        val slotCounts = mutableMapOf<SlotKey, Int>()
        for (point in raw) {
            val (slotIndex, adjustedDay) = slotFor(point.hourOfDay, point.dayOfWeek)
            val key = SlotKey(adjustedDay, slotIndex)
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

    /**
     * Détermine le créneau horaire UX auquel appartient une heure brute.
     *
     * Heures 0–3  → créneau 22h du jour précédent (nuit).
     * Heures 4–8  → créneau 6h  (matin).
     * Heures 9–12 → créneau 10h (fin de matinée).
     * Heures 13-16 → créneau 14h (après-midi).
     * Heures 17-20 → créneau 18h (soirée).
     * Heures 21-23 → créneau 22h (nuit).
     *
     * @return Pair(slotIndex, adjustedDayOfWeek). Le jour est décrémenté
     *         d'une unité (avec wrap, 0→6) pour les heures 0–3.
     */
    private fun slotFor(hourOfDay: Int, dayOfWeek: Int): Pair<Int, Int> {
        return when {
            hourOfDay in 0..3   -> 4 to ((dayOfWeek + 6) % 7)  // nuit → 22h, jour précédent
            hourOfDay in 4..8   -> 0 to dayOfWeek                // matin → 6h
            hourOfDay in 9..12  -> 1 to dayOfWeek                // fin de matinée → 10h
            hourOfDay in 13..16 -> 2 to dayOfWeek                // après-midi → 14h
            hourOfDay in 17..20 -> 3 to dayOfWeek                // soirée → 18h
            else                -> 4 to dayOfWeek                // 21-23h → 22h
        }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    private fun parseDateToEpochDay(date: String): Long? {
        return try {
            TimeUnit.MILLISECONDS.toDays(dateFormat.parse(date)!!.time)
        } catch (_: Exception) {
            null
        }
    }
}
