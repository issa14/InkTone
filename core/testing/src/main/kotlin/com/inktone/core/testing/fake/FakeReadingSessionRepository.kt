package com.inktone.core.testing.fake

import com.inktone.domain.model.DailyReadingStats
import com.inktone.domain.model.GlobalReadingStats
import com.inktone.domain.model.HeatmapPoint
import com.inktone.domain.model.ReadingSession
import com.inktone.domain.repository.ReadingSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FakeReadingSessionRepository : ReadingSessionRepository {
    private val state = MutableStateFlow<List<ReadingSession>>(emptyList())

    override suspend fun insert(session: ReadingSession) {
        state.value = state.value + session
    }

    override suspend fun getAllForPublication(publicationId: String): List<ReadingSession> =
        state.value.filter { it.publicationId == publicationId }

    override suspend fun getAll(): List<ReadingSession> = state.value

    override suspend fun getTotalDurationForDate(date: String): Long {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return state.value
            .filter { session ->
                val sessionDate = dateFormat.format(Date(session.startedAt))
                sessionDate == date
            }
            .sumOf { it.durationMs }
    }

    // Lot Statistiques Palier 1 — stubs pour les nouvelles méthodes d'agrégation
    override suspend fun getTotalStats(): GlobalReadingStats {
        val sessions = getAll()
        return GlobalReadingStats(
            totalVisualMs = sessions.sumOf { it.visualDurationMs },
            totalTtsMs = sessions.sumOf { it.ttsDurationMs },
            booksInteracted = sessions.map { it.publicationId }.distinct().size,
            totalWordsRead = sessions.sumOf { it.wordsRead.toLong() },
        )
    }

    override suspend fun getDailyStatsSince(sinceTimestamp: Long): List<DailyReadingStats> =
        getAll()
            .filter { it.startedAt >= sinceTimestamp }
            .groupBy {
                SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(it.startedAt))
            }
            .map { (date, sessions) ->
                DailyReadingStats(
                    date = date,
                    visualMs = sessions.sumOf { it.visualDurationMs },
                    ttsMs = sessions.sumOf { it.ttsDurationMs },
                )
            }
            .sortedBy { it.date }

    override suspend fun getHeatmapStatsSince(sinceTimestamp: Long): List<HeatmapPoint> =
        emptyList() // Stub minimal — non utilisé dans les tests existants

    override suspend fun getDistinctReadingDays(): List<String> =
        getAll()
            .map { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(it.startedAt)) }
            .distinct()
            .sortedDescending()

    override suspend fun getLastReadPublicationId(): String? =
        getAll().maxByOrNull { it.endedAt ?: 0L }?.publicationId

    override suspend fun getByPublicationId(bookId: String): List<ReadingSession> =
        getAll().filter { it.publicationId == bookId }

    // ───── Audit fix : requêtes ciblées ─────
    override suspend fun getRecentSessionsWithWords(limit: Int): List<ReadingSession> =
        getAll()
            .filter { it.wordsRead > 0 && it.durationMs > 0 }
            .sortedByDescending { it.startedAt }
            .take(limit)

    override suspend fun getDistinctPublicationIds(): List<String> =
        getAll().map { it.publicationId }.distinct()
}
