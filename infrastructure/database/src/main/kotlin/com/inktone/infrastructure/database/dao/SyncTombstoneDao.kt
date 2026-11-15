package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.inktone.infrastructure.database.entity.SyncTombstoneEntity

/**
 * Tombes de synchronisation (audit UX D61). Porte aussi les suppressions de signets et
 * d'annotations qui les écrivent : la suppression et sa tombe tiennent dans la même transaction,
 * sans quoi un arrêt entre les deux laisserait l'élément supprimé sans trace.
 */
@Dao
abstract class SyncTombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: SyncTombstoneEntity)

    @Query("SELECT * FROM sync_tombstones")
    abstract suspend fun getAll(): List<SyncTombstoneEntity>

    @Query("DELETE FROM sync_tombstones WHERE id = :id")
    abstract suspend fun remove(id: String)

    @Query("DELETE FROM sync_tombstones WHERE deletedAt < :before")
    abstract suspend fun purgeBefore(before: Long)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    protected abstract suspend fun deleteBookmarkRow(id: String)

    @Query("DELETE FROM annotations WHERE id = :id")
    protected abstract suspend fun deleteAnnotationRow(id: String)

    @Transaction
    open suspend fun deleteBookmark(id: String, deletedAt: Long) {
        deleteBookmarkRow(id)
        upsert(SyncTombstoneEntity(id, KIND_BOOKMARK, deletedAt))
    }

    @Transaction
    open suspend fun deleteAnnotation(id: String, deletedAt: Long) {
        deleteAnnotationRow(id)
        upsert(SyncTombstoneEntity(id, KIND_ANNOTATION, deletedAt))
    }

    companion object {
        const val KIND_BOOKMARK = "BOOKMARK"
        const val KIND_ANNOTATION = "ANNOTATION"
    }
}
