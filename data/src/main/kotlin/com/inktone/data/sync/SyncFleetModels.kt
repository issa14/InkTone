package com.inktone.data.sync

import com.inktone.domain.model.DeviceFleetEntry
import kotlinx.serialization.Serializable

/** Fichier de registre distant (tâche 11.8) — même dossier applicatif que les instantanés de sauvegarde, nom fixe. */
const val FLEET_REGISTRY_FILE_NAME = "device-fleet.json"

@Serializable
data class FleetRegistryPayload(val devices: List<DeviceFleetEntryDto> = emptyList())

@Serializable
data class DeviceFleetEntryDto(
    val deviceId: String,
    val displayName: String,
    val deviceType: String,
    val lastActiveAt: Long,
)

fun DeviceFleetEntry.toDto(): DeviceFleetEntryDto = DeviceFleetEntryDto(deviceId, displayName, deviceType, lastActiveAt)
fun DeviceFleetEntryDto.toDomain(): DeviceFleetEntry = DeviceFleetEntry(deviceId, displayName, deviceType, lastActiveAt)
