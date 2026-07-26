package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inktone.infrastructure.database.entity.VoiceProfileEntity

@Dao
interface VoiceProfileDao {
    @Query("SELECT * FROM voice_profiles WHERE id = :id")
    suspend fun getById(id: String): VoiceProfileEntity?

    @Query("SELECT * FROM voice_profiles")
    suspend fun getAll(): List<VoiceProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: VoiceProfileEntity)

    @Query("DELETE FROM voice_profiles WHERE id = :id")
    suspend fun delete(id: String)
}
