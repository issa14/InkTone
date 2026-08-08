package com.inktone.domain.repository

import com.inktone.domain.model.DailyReadingStats
import com.inktone.domain.model.GlobalReadingStats
import com.inktone.domain.model.HeatmapPoint
import com.inktone.domain.model.ReadingSession

/**
 * Dépôt des sessions de lecture (Lot Statistiques Palier 1).
 *
 * Les méthodes d'agrégation (`getTotalStats`, `getDailyStatsSince`,
 * `getHeatmapStatsSince`, `getDistinctReadingDays`) utilisent des
 * projections SQL natives — aucun chargement d'entités complètes
 * suivi d'une somme en mémoire.
 */
interface ReadingSessionRepository {
    suspend fun insert(session: ReadingSession)
    suspend fun getAllForPublication(publicationId: String): List<ReadingSession>
    suspend fun getAll(): List<ReadingSession>
    suspend fun getTotalDurationForDate(date: String): Long

    // Lot Statistiques Palier 1 — agrégations SQL-first
    suspend fun getTotalStats(): GlobalReadingStats
    suspend fun getDailyStatsSince(sinceTimestamp: Long): List<DailyReadingStats>
    suspend fun getHeatmapStatsSince(sinceTimestamp: Long): List<HeatmapPoint>
    suspend fun getDistinctReadingDays(): List<String>
    suspend fun getLastReadPublicationId(): String?
    suspend fun getByPublicationId(bookId: String): List<ReadingSession>

    // ───── Audit fix : requêtes ciblées (pas getAll) ─────
    suspend fun getRecentSessionsWithWords(limit: Int = 30): List<ReadingSession>
    suspend fun getDistinctPublicationIds(): List<String>
}
