package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.ReadingState
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.infrastructure.database.dao.ReadingStateDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomReadingStateRepository @Inject constructor(
    private val dao: ReadingStateDao,
) : ReadingStateRepository {
    override suspend fun get(publicationId: String): ReadingState? = dao.get(publicationId)?.toDomain()
    override fun observe(publicationId: String): Flow<ReadingState?> =
        dao.observe(publicationId).map { it?.toDomain() }
    override fun observeAll(): Flow<List<ReadingState>> =
        dao.observeAll().map { states -> states.map { it.toDomain() } }
    override suspend fun getAll(): List<ReadingState> = dao.getAll().map { it.toDomain() }
    override suspend fun save(state: ReadingState) = dao.save(state.toEntity())
    override suspend fun delete(publicationId: String) = dao.delete(publicationId)
}
