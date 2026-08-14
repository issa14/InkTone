package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inktone.infrastructure.database.entity.CatalogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT * FROM opds_catalogs ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<CatalogEntity>>

    @Query("SELECT * FROM opds_catalogs WHERE id = :id")
    suspend fun getById(id: String): CatalogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CatalogEntity)

    @Query("DELETE FROM opds_catalogs WHERE id = :id")
    suspend fun delete(id: String)
}
