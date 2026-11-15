package com.inktone.core.testing.fake

import com.inktone.domain.model.SyncTombstone
import com.inktone.domain.repository.SyncTombstoneRepository

class FakeSyncTombstoneRepository : SyncTombstoneRepository {
    private val tombstones = linkedMapOf<String, SyncTombstone>()

    override suspend fun getAll(): List<SyncTombstone> = tombstones.values.toList()

    override suspend fun record(tombstone: SyncTombstone) {
        tombstones[tombstone.id] = tombstone
    }

    override suspend fun remove(id: String) {
        tombstones.remove(id)
    }

    override suspend fun purgeBefore(before: Long) {
        tombstones.values.removeAll { it.deletedAt < before }
    }
}
