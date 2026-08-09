package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inktone.infrastructure.database.entity.CustomThemeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomThemeDao {
    @Query("SELECT * FROM custom_themes")
    fun observeAll(): Flow<List<CustomThemeEntity>>

    @Query("SELECT * FROM custom_themes WHERE id = :id")
    suspend fun getById(id: String): CustomThemeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: CustomThemeEntity)

    @Query("DELETE FROM custom_themes WHERE id = :id")
    suspend fun delete(id: String)
}
