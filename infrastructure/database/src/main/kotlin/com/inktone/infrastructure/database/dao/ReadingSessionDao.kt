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
}
