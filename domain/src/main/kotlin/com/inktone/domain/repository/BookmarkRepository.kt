package com.inktone.domain.repository

import com.inktone.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun observeForPublication(publicationId: String): Flow<List<Bookmark>>
    suspend fun insert(bookmark: Bookmark)
    suspend fun delete(id: String)
}
