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

        return StatisticsUiState(totalMs, finishedCount, streak)
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
