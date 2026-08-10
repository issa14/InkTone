package com.inktone.data.sync

import com.inktone.domain.model.SyncActivityEvent
import com.inktone.domain.model.SyncActivityEventType
import kotlinx.serialization.Serializable

const val ACTIVITY_LOG_FILE_NAME = "activity-log.json"

/** Plafond du journal (tâche 11.8 : « 5 à 10 derniers événements »). */
const val ACTIVITY_LOG_MAX_EVENTS = 10

@Serializable
data class ActivityLogPayload(val events: List<SyncActivityEventDto> = emptyList())

@Serializable
data class SyncActivityEventDto(
    val id: String,
    val type: String,
    val message: String,
    val occurredAt: Long,
)

fun SyncActivityEvent.toDto(): SyncActivityEventDto = SyncActivityEventDto(id, type.name, message, occurredAt)
fun SyncActivityEventDto.toDomain(): SyncActivityEvent =
    SyncActivityEvent(id, runCatching { SyncActivityEventType.valueOf(type) }.getOrDefault(SyncActivityEventType.SUCCESS), message, occurredAt)
