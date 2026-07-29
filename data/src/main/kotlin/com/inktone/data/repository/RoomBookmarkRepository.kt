package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.Bookmark
import com.inktone.domain.repository.BookmarkRepository
import com.inktone.infrastructure.database.dao.BookmarkDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomBookmarkRepository @Inject constructor(
    private val dao: BookmarkDao,
) : BookmarkRepository {
    override fun observeForPublication(publicationId: String): Flow<List<Bookmark>> =
        dao.observeForPublication(publicationId).map { list -> list.map { it.toDomain() } }
    override fun observeAll(): Flow<List<Bookmark>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun insert(bookmark: Bookmark) = dao.insert(bookmark.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
}
