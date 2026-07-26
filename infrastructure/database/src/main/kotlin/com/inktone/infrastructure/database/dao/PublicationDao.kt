package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.inktone.infrastructure.database.entity.PublicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PublicationDao {
    @Query("SELECT * FROM publications ORDER BY lastOpened DESC")
    fun observeAll(): Flow<List<PublicationEntity>>

    @Query("SELECT * FROM publications WHERE id = :id")
    suspend fun getById(id: String): PublicationEntity?

    @Query("SELECT * FROM publications WHERE fileHash = :hash LIMIT 1")
    suspend fun getByFileHash(hash: String): PublicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PublicationEntity)

    @Update
    suspend fun update(entity: PublicationEntity)

    @Query("DELETE FROM publications WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE publications SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)
}
