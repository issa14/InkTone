package com.inktone.data.repository

import com.inktone.data.sync.ACTIVITY_LOG_FILE_NAME
import com.inktone.data.sync.ACTIVITY_LOG_MAX_EVENTS
import com.inktone.data.sync.ActivityLogPayload
import com.inktone.data.sync.toDomain
import com.inktone.data.sync.toDto
import com.inktone.domain.model.SyncActivityEvent
import com.inktone.domain.repository.SyncActivityLogRepository
import com.inktone.domain.service.SyncProvider
import kotlinx.serialization.json.Json
import javax.inject.Inject

private val activityLogJson = Json { ignoreUnknownKeys = true }

/** Journal distant plafonné (tâche 11.8) — même patron relire-avant-écrire que [RemoteDeviceFleetRepository]. */
class RemoteSyncActivityLogRepository @Inject constructor(
    private val syncProvider: SyncProvider,
) : SyncActivityLogRepository {

    override suspend fun listEvents(): List<SyncActivityEvent> {
        val bytes = syncProvider.download(ACTIVITY_LOG_FILE_NAME) ?: return emptyList()
        return runCatching { activityLogJson.decodeFromString(ActivityLogPayload.serializer(), bytes.decodeToString()) }
            .getOrDefault(ActivityLogPayload())
            .events.map { it.toDomain() }
            .sortedByDescending { it.occurredAt }
    }

    override suspend fun appendEvent(event: SyncActivityEvent) {
        val updated = (listOf(event) + listEvents()).take(ACTIVITY_LOG_MAX_EVENTS)
        val json = activityLogJson.encodeToString(ActivityLogPayload.serializer(), ActivityLogPayload(updated.map { it.toDto() }))
        syncProvider.upload(ACTIVITY_LOG_FILE_NAME, json.encodeToByteArray())
    }
}
