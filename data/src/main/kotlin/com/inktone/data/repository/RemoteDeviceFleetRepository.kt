package com.inktone.data.repository

import com.inktone.data.sync.FLEET_REGISTRY_FILE_NAME
import com.inktone.data.sync.FleetRegistryPayload
import com.inktone.data.sync.toDomain
import com.inktone.data.sync.toDto
import com.inktone.domain.model.DeviceFleetEntry
import com.inktone.domain.repository.SyncFleetRepository
import com.inktone.domain.service.SyncProvider
import kotlinx.serialization.json.Json
import javax.inject.Inject

private val fleetJson = Json { ignoreUnknownKeys = true }

/**
 * Registre distant (tâche 11.8) — un seul fichier partagé par tous les
 * appareils du compte. [touchCurrentDevice] relit l'intégralité avant
 * d'écrire pour ne remplacer que sa propre entrée : deux appareils qui se
 * synchronisent au même instant peuvent encore se marcher dessus (aucun
 * verrou distant disponible côté Drive), mais celui qui écrit en dernier
 * ne fait disparaître AUCUN autre appareil, seulement potentiellement sa
 * propre mise à jour la plus récente — acceptable, contrairement à un
 * remplacement aveugle du fichier entier.
 */
class RemoteDeviceFleetRepository @Inject constructor(
    private val syncProvider: SyncProvider,
) : SyncFleetRepository {

    override suspend fun listDevices(): List<DeviceFleetEntry> {
        val bytes = syncProvider.download(FLEET_REGISTRY_FILE_NAME) ?: return emptyList()
        return runCatching { fleetJson.decodeFromString(FleetRegistryPayload.serializer(), bytes.decodeToString()) }
            .getOrDefault(FleetRegistryPayload())
            .devices.map { it.toDomain() }
    }

    override suspend fun touchCurrentDevice(entry: DeviceFleetEntry) {
        val updated = listDevices().filterNot { it.deviceId == entry.deviceId } + entry
        write(updated)
    }

    override suspend fun removeDevice(deviceId: String) {
        write(listDevices().filterNot { it.deviceId == deviceId })
    }

    private suspend fun write(devices: List<DeviceFleetEntry>) {
        val json = fleetJson.encodeToString(FleetRegistryPayload.serializer(), FleetRegistryPayload(devices.map { it.toDto() }))
        syncProvider.upload(FLEET_REGISTRY_FILE_NAME, json.encodeToByteArray())
    }
}
