package com.inktone.data.repository

import com.inktone.domain.model.SyncTombstone
import com.inktone.domain.model.TombstoneKind
import com.inktone.domain.repository.SyncTombstoneRepository
import com.inktone.infrastructure.database.dao.SyncTombstoneDao
import com.inktone.infrastructure.database.entity.SyncTombstoneEntity
import javax.inject.Inject

class RoomSyncTombstoneRepository @Inject constructor(
    private val dao: SyncTombstoneDao,
) : SyncTombstoneRepository {
    override suspend fun getAll(): List<SyncTombstone> = dao.getAll().mapNotNull { entity ->
        val kind = runCatching { TombstoneKind.valueOf(entity.kind) }.getOrNull() ?: return@mapNotNull null
        SyncTombstone(entity.id, kind, entity.deletedAt)
    }

    override suspend fun record(tombstone: SyncTombstone) =
        dao.upsert(SyncTombstoneEntity(tombstone.id, tombstone.kind.name, tombstone.deletedAt))

    override suspend fun remove(id: String) = dao.remove(id)

    override suspend fun purgeBefore(before: Long) = dao.purgeBefore(before)
}
