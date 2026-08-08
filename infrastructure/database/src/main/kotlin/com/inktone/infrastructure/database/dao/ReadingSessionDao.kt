package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.inktone.infrastructure.database.entity.ReadingSessionEntity
import com.inktone.infrastructure.database.projection.DailyStatsProjection
import com.inktone.infrastructure.database.projection.HeatmapProjection
import com.inktone.infrastructure.database.projection.TotalStatsProjection

/**
 * DAO SQL-first pour les statistiques de lecture (Lot Statistiques Palier 1).
 *
 * Toutes les agrégations se font ici — aucun `.sumOf {}` ni `.groupBy {}`
 * en Kotlin pour l'historique complet. Les fonctions `date()` et `strftime()`
 * de SQLite utilisent `startedAt / 1000` (conversion ms → secondes Unix)
 * avec le modificateur `localtime` pour le fuseau horaire de l'utilisateur.
 */
@Dao
interface ReadingSessionDao {

    // ───── KPIs Globaux ─────

    @Query("""
        SELECT 
            SUM(visualDurationMs) as totalVisualMs,
            SUM(ttsDurationMs) as totalTtsMs,
            COUNT(DISTINCT publicationId) as booksInteracted
        FROM reading_sessions
    """)
    suspend fun getTotalStats(): TotalStatsProjection

    // ───── Histogramme quotidien ─────

    @Query("""
        SELECT 
            date(startedAt / 1000, 'unixepoch', 'localtime') as date,
            SUM(visualDurationMs) as visualMs,
            SUM(ttsDurationMs) as ttsMs
        FROM reading_sessions
        WHERE startedAt >= :sinceTimestamp
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getDailyStatsSince(sinceTimestamp: Long): List<DailyStatsProjection>

    // ───── Heatmap (jour de semaine × heure) ─────

    @Query("""
        SELECT 
            cast(strftime('%w', startedAt / 1000, 'unixepoch', 'localtime') as INTEGER) as dayOfWeek,
            cast(strftime('%H', startedAt / 1000, 'unixepoch', 'localtime') as INTEGER) as hourOfDay,
            COUNT(*) as interactionCount
        FROM reading_sessions
        WHERE startedAt >= :sinceTimestamp
        GROUP BY dayOfWeek, hourOfDay
    """)
    suspend fun getHeatmapStatsSince(sinceTimestamp: Long): List<HeatmapProjection>

    // ───── Streak : dates distinctes triées décroissantes ─────

    @Query("""
        SELECT DISTINCT date(startedAt / 1000, 'unixepoch', 'localtime') 
        FROM reading_sessions 
        ORDER BY 1 DESC
    """)
    suspend fun getDistinctReadingDays(): List<String>

    // ───── Livre en cours (Section 3) — dernière publication lue ─────

    @Query("""
        SELECT publicationId 
        FROM reading_sessions 
        ORDER BY endedAt DESC 
        LIMIT 1
    """)
    suspend fun getLastReadPublicationId(): String?

    // ───── Détail par livre (Section 4) — sessions d'une publication ─────

    @Query("SELECT * FROM reading_sessions WHERE publicationId = :bookId ORDER BY startedAt DESC")
    suspend fun getByPublicationId(bookId: String): List<ReadingSessionEntity>

    // ───── Audit fix : sessions récentes avec mots pour WPM (pas getAll) ─────

    @Query("""
        SELECT * FROM reading_sessions 
        WHERE wordsRead > 0 AND (visualDurationMs + ttsDurationMs) > 0 
        ORDER BY startedAt DESC 
        LIMIT :limit
    """)
    suspend fun getRecentSessionsWithWords(limit: Int = 30): List<ReadingSessionEntity>

    // ───── Audit fix : publicationIds distincts pour le sélecteur ─────

    @Query("SELECT DISTINCT publicationId FROM reading_sessions")
    suspend fun getDistinctPublicationIds(): List<String>

    // ───── Méthodes existantes conservées pour compatibilité ─────

    @Insert
    suspend fun insert(entity: ReadingSessionEntity)

    @Query("SELECT * FROM reading_sessions WHERE publicationId = :publicationId")
    suspend fun getAllForPublication(publicationId: String): List<ReadingSessionEntity>

    @Query("SELECT * FROM reading_sessions")
    suspend fun getAll(): List<ReadingSessionEntity>

    @Query("""
        SELECT COALESCE(SUM(visualDurationMs + ttsDurationMs), 0) 
        FROM reading_sessions 
        WHERE date(startedAt / 1000, 'unixepoch', 'localtime') = :date
    """)
    suspend fun getTotalDurationForDate(date: String): Long
}
