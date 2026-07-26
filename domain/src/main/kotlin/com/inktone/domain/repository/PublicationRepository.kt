package com.inktone.domain.repository

import com.inktone.domain.model.Publication
import kotlinx.coroutines.flow.Flow

interface PublicationRepository {
    fun observeAll(): Flow<List<Publication>>
    suspend fun getById(id: String): Publication?
    suspend fun getByFileHash(hash: String): Publication?
    suspend fun insert(publication: Publication)
    suspend fun update(publication: Publication)
    suspend fun delete(id: String)
    suspend fun setFavorite(id: String, isFavorite: Boolean)
}
