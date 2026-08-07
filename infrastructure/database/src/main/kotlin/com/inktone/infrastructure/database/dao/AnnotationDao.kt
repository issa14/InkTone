package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.inktone.infrastructure.database.entity.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query(
        "SELECT * FROM annotations WHERE publicationId = :publicationId " +
            "ORDER BY startChapterIndex, startCharOffset",
    )
    fun observeForPublication(publicationId: String): Flow<List<AnnotationEntity>>

    @Insert
    suspend fun insert(entity: AnnotationEntity)

    @Update
    suspend fun update(entity: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE annotations SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean)
}
