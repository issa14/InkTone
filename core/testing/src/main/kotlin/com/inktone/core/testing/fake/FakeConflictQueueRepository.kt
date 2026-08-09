package com.inktone.core.testing.fake

import com.inktone.domain.model.PositionConflict
import com.inktone.domain.repository.ConflictQueueRepository

class FakeConflictQueueRepository : ConflictQueueRepository {
    private val pending = mutableMapOf<String, PositionConflict>()

    override suspend fun listPending(): List<PositionConflict> = pending.values.toList()

    override suspend fun enqueue(conflict: PositionConflict) {
        pending.putIfAbsent(conflict.publicationId, conflict)
    }

    override suspend fun remove(publicationId: String) {
        pending.remove(publicationId)
    }
}
