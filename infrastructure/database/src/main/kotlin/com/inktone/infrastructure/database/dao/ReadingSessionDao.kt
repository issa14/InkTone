package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.inktone.infrastructure.database.entity.ReadingSessionEntity

@Dao
interface ReadingSessionDao {
    @Insert
    suspend fun insert(entity: ReadingSessionEntity)

    @Query("SELECT * FROM reading_sessions WHERE publicationId = :publicationId")
    suspend fun getAllForPublication(publicationId: String): List<ReadingSessionEntity>

    @Query("SELECT * FROM reading_sessions")
    suspend fun getAll(): List<ReadingSessionEntity>

    /**
     * Tache 1.4 (Partie 1) — somme des durees de session pour une date
     * donnee (format "yyyy-MM-dd"). `startedAt` est un timestamp Unix
     * en millisecondes ; on le divise par 1000 pour obtenir des secondes
     * utilisables par la fonction `date()` de SQLite.
     */
    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM reading_sessions WHERE date(startedAt / 1000, 'unixepoch') = :date")
    suspend fun getTotalDurationForDate(date: String): Long
}
