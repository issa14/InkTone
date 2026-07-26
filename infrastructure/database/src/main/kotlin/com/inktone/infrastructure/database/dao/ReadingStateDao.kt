package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inktone.infrastructure.database.entity.ReadingStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingStateDao {
    @Query("SELECT * FROM reading_states WHERE publicationId = :publicationId")
    suspend fun get(publicationId: String): ReadingStateEntity?

    @Query("SELECT * FROM reading_states WHERE publicationId = :publicationId")
    fun observe(publicationId: String): Flow<ReadingStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: ReadingStateEntity)

    @Query("DELETE FROM reading_states WHERE publicationId = :publicationId")
    suspend fun delete(publicationId: String)
}
