package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inktone.infrastructure.database.entity.PendingConflictEntity

@Dao
interface PendingConflictDao {
    @Query("SELECT * FROM pending_conflicts")
    suspend fun getAll(): List<PendingConflictEntity>

    /** IGNORE (pas ABORT) : une file déjà occupée pour cette publication n'est jamais écrasée — idempotent entre synchros répétées avant résolution. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PendingConflictEntity)

    @Query("DELETE FROM pending_conflicts WHERE publicationId = :publicationId")
    suspend fun delete(publicationId: String)
}
