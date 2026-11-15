package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.Bookmark
import com.inktone.domain.repository.BookmarkRepository
import com.inktone.infrastructure.database.dao.BookmarkDao
import com.inktone.infrastructure.database.dao.SyncTombstoneDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomBookmarkRepository @Inject constructor(
    private val dao: BookmarkDao,
    private val tombstones: SyncTombstoneDao,
) : BookmarkRepository {
    override fun observeForPublication(publicationId: String): Flow<List<Bookmark>> =
        dao.observeForPublication(publicationId).map { list -> list.map { it.toDomain() } }
    override fun observeAll(): Flow<List<Bookmark>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun insert(bookmark: Bookmark) = dao.insert(bookmark.toEntity())
    override suspend fun update(bookmark: Bookmark) = dao.update(bookmark.toEntity())
    // La suppression écrit sa tombe dans la même transaction (audit UX D61).
    override suspend fun delete(id: String) = tombstones.deleteBookmark(id, System.currentTimeMillis())
    override suspend fun setPinned(id: String, isPinned: Boolean) = dao.setPinned(id, isPinned)
    override suspend fun updateNote(id: String, note: String?) = dao.updateNote(id, note, System.currentTimeMillis())
}
