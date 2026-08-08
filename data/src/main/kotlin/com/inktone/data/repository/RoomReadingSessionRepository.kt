package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.DailyReadingStats
import com.inktone.domain.model.GlobalReadingStats
import com.inktone.domain.model.HeatmapPoint
import com.inktone.domain.model.ReadingSession
import com.inktone.domain.repository.ReadingSessionRepository
import com.inktone.infrastructure.database.dao.ReadingSessionDao
import javax.inject.Inject

class RoomReadingSessionRepository @Inject constructor(
    private val dao: ReadingSessionDao,
) : ReadingSessionRepository {
    override suspend fun insert(session: ReadingSession) = dao.insert(session.toEntity())
    override suspend fun getAllForPublication(publicationId: String): List<ReadingSession> =
        dao.getAllForPublication(publicationId).map { it.toDomain() }
    override suspend fun getAll(): List<ReadingSession> = dao.getAll().map { it.toDomain() }
    override suspend fun getTotalDurationForDate(date: String): Long = dao.getTotalDurationForDate(date)

    // Lot Statistiques Palier 1 — délégation directe aux projections DAO
    override suspend fun getTotalStats(): GlobalReadingStats {
        val p = dao.getTotalStats()
        return GlobalReadingStats(p.totalVisualMs, p.totalTtsMs, p.booksInteracted)
    }

    override suspend fun getDailyStatsSince(sinceTimestamp: Long): List<DailyReadingStats> =
        dao.getDailyStatsSince(sinceTimestamp).map {
            DailyReadingStats(it.date, it.visualMs, it.ttsMs)
        }

    override suspend fun getHeatmapStatsSince(sinceTimestamp: Long): List<HeatmapPoint> =
        dao.getHeatmapStatsSince(sinceTimestamp).map {
            HeatmapPoint(it.dayOfWeek, it.hourOfDay, it.interactionCount)
        }

    override suspend fun getDistinctReadingDays(): List<String> = dao.getDistinctReadingDays()
    override suspend fun getLastReadPublicationId(): String? = dao.getLastReadPublicationId()
    override suspend fun getByPublicationId(bookId: String): List<ReadingSession> =
        dao.getByPublicationId(bookId).map { it.toDomain() }

    // ───── Audit fix : requêtes ciblées ─────
    override suspend fun getRecentSessionsWithWords(limit: Int): List<ReadingSession> =
        dao.getRecentSessionsWithWords(limit).map { it.toDomain() }
    override suspend fun getDistinctPublicationIds(): List<String> =
        dao.getDistinctPublicationIds()
}
