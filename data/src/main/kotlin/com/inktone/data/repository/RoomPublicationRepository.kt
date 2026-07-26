package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.Publication
import com.inktone.domain.repository.PublicationRepository
import com.inktone.infrastructure.database.dao.PublicationDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomPublicationRepository @Inject constructor(
    private val dao: PublicationDao,
) : PublicationRepository {
    override fun observeAll(): Flow<List<Publication>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun getById(id: String): Publication? = dao.getById(id)?.toDomain()
    override suspend fun getByFileHash(hash: String): Publication? = dao.getByFileHash(hash)?.toDomain()
    override suspend fun insert(publication: Publication) = dao.insert(publication.toEntity())
    override suspend fun update(publication: Publication) = dao.update(publication.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun setFavorite(id: String, isFavorite: Boolean) = dao.setFavorite(id, isFavorite)
}
