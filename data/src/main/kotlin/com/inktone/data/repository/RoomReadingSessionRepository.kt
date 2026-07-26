package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.ReadingSession
import com.inktone.domain.repository.ReadingSessionRepository
import com.inktone.infrastructure.database.dao.ReadingSessionDao
import javax.inject.Inject

class RoomReadingSessionRepository @Inject constructor(
    private val dao: ReadingSessionDao,
) : ReadingSessionRepository {
    override suspend fun insert(session: ReadingSession) = dao.insert(session.toEntity())
    override suspend fun getAllForPublication(publicationId: String): List<ReadingSession> =
        dao.getAllForPublication(publicationId).map { it.toDomain() }
    override suspend fun getAll(): List<ReadingSession> = dao.getAll().map { it.toDomain() }
}
