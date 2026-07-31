package com.inktone.domain.usecase

import com.inktone.domain.model.FilterMode
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingSessionRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

data class StatisticsUiState(
    val totalReadingTimeMs: Long = 0,
    val booksFinished: Int = 0,
    val currentStreakDays: Int = 0,
    // D.4 — nouvelles métriques
    val averageWpm: Int = 0,
    val maxStreakDays: Int = 0,
    val dailyGoalMinutes: Int = 20,
    val todayReadingMinutes: Long = 0,
)

/**
 * "Livre termine" reutilise exactement `FilterMode.READ`
 * (`publicationRepository.observeFiltered`, Tache 6.5.2) plutot qu'une
 * deuxieme heuristique Kotlin qui pourrait diverger silencieusement du
 * chiffre affiche dans les filtres de bibliotheque.
 */
class GetStatisticsUseCase(
    private val readingSessionRepository: ReadingSessionRepository,
    private val publicationRepository: PublicationRepository,
) {
    suspend operator fun invoke(): StatisticsUiState {
        val sessions = readingSessionRepository.getAll()
        val totalMs = sessions.sumOf { it.durationMs }

        val finishedCount = publicationRepository.observeFiltered(FilterMode.READ).first().size

        val streak = computeStreak(sessions.map { it.startedAt })

        // D.4 — WPM moyen sur les sessions avec mots lus
        val sessionsWithWords = sessions.filter { it.wordsRead > 0 && it.durationMs > 0 }
        val averageWpm = if (sessionsWithWords.isNotEmpty()) {
            val totalWords = sessionsWithWords.sumOf { it.wordsRead }
            val totalMinutes = sessionsWithWords.sumOf { it.durationMs } / 60_000.0
            if (totalMinutes > 0) (totalWords / totalMinutes).toInt() else 0
        } else 0

        // D.4 — Record de série
        val maxStreak = computeMaxStreak(sessions.map { it.startedAt })

        // D.4 — Lecture du jour (aujourd'hui)
        val todayStart = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        val todaySessions = sessions.filter {
            TimeUnit.MILLISECONDS.toDays(it.startedAt) == todayStart
        }
        val todayReadingMinutes = todaySessions.sumOf { it.durationMs } / 60_000L

        return StatisticsUiState(
            totalReadingTimeMs = totalMs,
            booksFinished = finishedCount,
            currentStreakDays = streak,
            averageWpm = averageWpm,
            maxStreakDays = maxStreak,
            todayReadingMinutes = todayReadingMinutes,
        )
    }

    private fun computeMaxStreak(sessionStarts: List<Long>): Int {
        if (sessionStarts.isEmpty()) return 0
        val days = sessionStarts.map { TimeUnit.MILLISECONDS.toDays(it) }.distinct().sorted()
        var maxStreak = 1
        var currentStreak = 1
        for (i in 1 until days.size) {
            if (days[i] == days[i - 1] + 1) {
                currentStreak++
                maxStreak = maxOf(maxStreak, currentStreak)
            } else {
                currentStreak = 1
            }
        }
        return maxStreak
    }

    private fun computeStreak(sessionStarts: List<Long>): Int {
        if (sessionStarts.isEmpty()) return 0
        val days = sessionStarts.map { TimeUnit.MILLISECONDS.toDays(it) }.toSortedSet().toList().sortedDescending()
        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())

        if (days.first() != today && days.first() != today - 1) return 0

        var streak = 1
        var expected = days.first() - 1
        for (day in days.drop(1)) {
            if (day == expected) {
                streak++
                expected--
            } else if (day < expected) {
                break
            }
        }
        return streak
    }
}
