package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.PositionConflict
import com.inktone.domain.repository.ConflictQueueRepository
import com.inktone.infrastructure.database.dao.PendingConflictDao
import javax.inject.Inject

class RoomConflictQueueRepository @Inject constructor(
    private val dao: PendingConflictDao,
) : ConflictQueueRepository {
    override suspend fun listPending(): List<PositionConflict> = dao.getAll().map { it.toDomain() }
    override suspend fun enqueue(conflict: PositionConflict) = dao.insert(conflict.toEntity())
    override suspend fun remove(publicationId: String) = dao.delete(publicationId)
}
