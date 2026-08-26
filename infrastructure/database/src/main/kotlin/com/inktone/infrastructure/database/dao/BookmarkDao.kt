package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.inktone.infrastructure.database.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE publicationId = :publicationId")
    fun observeForPublication(publicationId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(entity: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE bookmarks SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean)

    // Lot 21, tâche 5 — note optionnelle d'un signet.
    @Query("UPDATE bookmarks SET note = :note WHERE id = :id")
    suspend fun updateNote(id: String, note: String?)
}
