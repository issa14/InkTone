package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inktone.infrastructure.database.entity.PronunciationRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PronunciationRuleDao {
    @Query("SELECT * FROM pronunciation_rules")
    fun observeAll(): Flow<List<PronunciationRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: PronunciationRuleEntity)

    @Query("DELETE FROM pronunciation_rules WHERE id = :id")
    suspend fun delete(id: String)
}
