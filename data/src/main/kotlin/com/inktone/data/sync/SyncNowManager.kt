package com.inktone.data.sync

import com.inktone.data.backup.BackupManager
import com.inktone.data.backup.backupJson
import com.inktone.domain.model.DeviceFleetEntry
import com.inktone.domain.model.SyncActivityEvent
import com.inktone.domain.model.SyncActivityEventType
import com.inktone.domain.repository.DeviceIdentityRepository
import com.inktone.domain.repository.SyncAccountRepository
import com.inktone.domain.repository.SyncActivityLogRepository
import com.inktone.domain.repository.SyncFleetRepository
import com.inktone.domain.service.SyncFailureReason
import com.inktone.domain.service.SyncNowService
import com.inktone.domain.service.SyncOperation
import com.inktone.domain.service.SyncOperationResult
import com.inktone.domain.service.SyncOperationTracker
import com.inktone.domain.service.SyncProvider
import kotlinx.serialization.encodeToString
import java.util.UUID
import javax.inject.Inject

/**
 * Implémente [SyncNowService] (tâche 11.8) — orchestre un instantané par
 * appareil : téléverse la charge utile locale sous `snapshot-<deviceId>
 * .json`, met à jour la flotte et le journal, reflète le résultat sur
 * [SyncAccountRepository]. Vit dans `data` (pas `domain/usecase`) : a
 * besoin de `BackupManager`/`BackupPayload`, des types de sérialisation
 * indisponibles depuis `domain`.
 */
class SyncNowManager @Inject constructor(
    private val syncProvider: SyncProvider,
    private val backupManager: BackupManager,
    private val syncOperationTracker: SyncOperationTracker,
    private val syncAccountRepository: SyncAccountRepository,
    private val syncFleetRepository: SyncFleetRepository,
    private val syncActivityLogRepository: SyncActivityLogRepository,
    private val deviceIdentityRepository: DeviceIdentityRepository,
) : SyncNowService {

    override suspend fun synchronizeNow(): SyncOperationResult {
        if (syncOperationTracker.observe().value == SyncOperation.SYNCING) {
            // Tâche 11.9, point 6 — un second déclenchement pendant un
            // transfert en cours est refusé ici, source unique de vérité
            // (l'UI désactive aussi le bouton, mais ne doit pas être la
            // seule garde : le déclenchement automatique en arrière-plan
            // n'a pas de bouton à désactiver).
            return SyncOperationResult.Failed(SyncFailureReason.UNKNOWN, "Une synchronisation est déjà en cours")
        }

        syncOperationTracker.begin(SyncOperation.SYNCING)
        try {
            val device = deviceIdentityRepository.getOrCreate()
            val payload = backupManager.buildPayloadForSync()
            val json = backupJson.encodeToString(payload)
            val result = syncProvider.upload("snapshot-${device.id}.json", json.encodeToByteArray())
            val now = System.currentTimeMillis()

            when (result) {
                is SyncOperationResult.Success -> {
                    syncFleetRepository.touchCurrentDevice(
                        DeviceFleetEntry(deviceId = device.id, displayName = device.displayName, lastActiveAt = now),
                    )
                    syncActivityLogRepository.appendEvent(
                        SyncActivityEvent(UUID.randomUUID().toString(), SyncActivityEventType.SUCCESS, "Synchronisation réussie", now),
                    )
                    syncAccountRepository.markSyncSucceeded(now)
                }
                is SyncOperationResult.Failed -> {
                    syncActivityLogRepository.appendEvent(
                        SyncActivityEvent(UUID.randomUUID().toString(), SyncActivityEventType.NETWORK_FAILURE, result.message, now),
                    )
                    syncAccountRepository.markSyncFailed()
                }
            }
            return result
        } finally {
            syncOperationTracker.end()
        }
    }
}
