package com.inktone.data.sync

import com.inktone.data.backup.BackupManager
import com.inktone.data.backup.BackupPayload
import com.inktone.data.backup.backupJson
import com.inktone.data.backup.toDomain
import com.inktone.domain.model.DeviceFleetEntry
import com.inktone.domain.model.PositionConflict
import com.inktone.domain.model.ReadingPositionSnapshot
import com.inktone.domain.model.SyncActivityEvent
import com.inktone.domain.model.SyncActivityEventType
import com.inktone.domain.repository.AnnotationRepository
import com.inktone.domain.repository.BookmarkRepository
import com.inktone.domain.repository.ConflictQueueRepository
import com.inktone.domain.repository.DeviceIdentityRepository
import com.inktone.domain.repository.PronunciationRuleRepository
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.repository.SyncAccountRepository
import com.inktone.domain.repository.SyncActivityLogRepository
import com.inktone.domain.repository.SyncFleetRepository
import com.inktone.domain.repository.ThemeRepository
import com.inktone.domain.service.SyncFailureReason
import com.inktone.domain.service.SyncNowService
import com.inktone.domain.service.SyncOperation
import com.inktone.domain.service.SyncOperationResult
import com.inktone.domain.service.SyncOperationTracker
import com.inktone.domain.service.SyncProvider
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import java.util.UUID
import javax.inject.Inject

private const val SNAPSHOT_PREFIX = "snapshot-"
private const val SNAPSHOT_SUFFIX = ".json"

/**
 * Implémente [SyncNowService] (tâche 11.8, étendue tâche 11.10) —
 * téléverse un instantané propre à l'appareil courant, puis télécharge
 * et fusionne les instantanés des **autres** appareils :
 * - bookmarks/annotations/règles de prononciation/thèmes personnalisés :
 *   fusion silencieuse par union d'identifiants (tâche 11.10, matrice de
 *   résolution) — jamais de conflit posé à l'utilisateur pour ces
 *   catégories.
 * - position de lecture : divergence détectée, mise en file
 *   ([ConflictQueueRepository]) plutôt que tranchée ici — la synchro
 *   (manuelle ou en arrière-plan) ne peut jamais arbitrer elle-même.
 *
 * **Écart déclaré (tâche 11.10/11.11)** : aucun marqueur de suppression
 * horodaté n'existe encore pour bookmarks/annotations — un élément
 * supprimé localement peut réapparaître si un autre appareil le
 * televerse encore. La matrice de résolution l'exige ; son
 * implémentation (retrofit des chemins de suppression existants pour
 * écrire une tombe plutôt qu'un DELETE dur) est un chantier à part,
 * volontairement hors de ce palier plutôt que retrofitté en hâte.
 */
class SyncNowManager @Inject constructor(
    private val syncProvider: SyncProvider,
    private val backupManager: BackupManager,
    private val syncOperationTracker: SyncOperationTracker,
    private val syncAccountRepository: SyncAccountRepository,
    private val syncFleetRepository: SyncFleetRepository,
    private val syncActivityLogRepository: SyncActivityLogRepository,
    private val deviceIdentityRepository: DeviceIdentityRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val annotationRepository: AnnotationRepository,
    private val pronunciationRuleRepository: PronunciationRuleRepository,
    private val themeRepository: ThemeRepository,
    private val readingStateRepository: ReadingStateRepository,
    private val publicationRepository: PublicationRepository,
    private val conflictQueueRepository: ConflictQueueRepository,
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
            val result = syncProvider.upload("$SNAPSHOT_PREFIX${device.id}$SNAPSHOT_SUFFIX", json.encodeToByteArray())
            val now = System.currentTimeMillis()

            when (result) {
                is SyncOperationResult.Success -> {
                    mergeRemoteSnapshots(currentDeviceId = device.id, currentDeviceLabel = device.displayName)
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

    private suspend fun mergeRemoteSnapshots(currentDeviceId: String, currentDeviceLabel: String) {
        val ownFileName = "$SNAPSHOT_PREFIX$currentDeviceId$SNAPSHOT_SUFFIX"
        val remoteFiles = syncProvider.list().filter {
            it.name.startsWith(SNAPSHOT_PREFIX) && it.name.endsWith(SNAPSHOT_SUFFIX) && it.name != ownFileName
        }
        if (remoteFiles.isEmpty()) return

        val fleetLabels = syncFleetRepository.listDevices().associate { it.deviceId to it.displayName }
        val localBookmarkIds = bookmarkRepository.observeAll().first().map { it.id }.toMutableSet()
        val localAnnotationIds = annotationRepository.getAll().map { it.id }.toMutableSet()
        val pendingConflictPublicationIds = conflictQueueRepository.listPending().map { it.publicationId }.toMutableSet()

        for (file in remoteFiles) {
            val bytes = syncProvider.download(file.name) ?: continue
            val payload = runCatching { backupJson.decodeFromString(BackupPayload.serializer(), bytes.decodeToString()) }.getOrNull() ?: continue
            val remoteDeviceId = file.name.removePrefix(SNAPSHOT_PREFIX).removeSuffix(SNAPSHOT_SUFFIX)
            val remoteDeviceLabel = fleetLabels[remoteDeviceId] ?: remoteDeviceId

            // Fusion silencieuse — additive, jamais de question posée.
            payload.bookmarks.forEach { backup ->
                if (backup.id !in localBookmarkIds && publicationRepository.getById(backup.publicationId) != null) {
                    bookmarkRepository.insert(backup.toDomain())
                    localBookmarkIds += backup.id
                }
            }
            payload.annotations.forEach { backup ->
                if (backup.id !in localAnnotationIds && publicationRepository.getById(backup.publicationId) != null) {
                    annotationRepository.insert(backup.toDomain())
                    localAnnotationIds += backup.id
                }
            }
            // Réglages/thèmes : dernier écrit gagne (REPLACE au niveau DAO).
            payload.pronunciationRules.forEach { pronunciationRuleRepository.save(it.toDomain()) }
            payload.customThemes.forEach { themeRepository.saveCustom(it.toDomain()) }

            // Position de lecture — jamais fusionnée ni tranchée ici.
            payload.readingStates.forEach { remoteBackup ->
                if (remoteBackup.publicationId in pendingConflictPublicationIds) return@forEach
                val publication = publicationRepository.getById(remoteBackup.publicationId) ?: return@forEach
                val remoteState = remoteBackup.toDomain()
                val localState = readingStateRepository.get(remoteBackup.publicationId)

                if (localState == null) {
                    readingStateRepository.save(remoteState)
                } else if (localState.locator != remoteState.locator) {
                    conflictQueueRepository.enqueue(
                        PositionConflict(
                            publicationId = remoteBackup.publicationId,
                            bookTitle = publication.title,
                            local = ReadingPositionSnapshot(
                                locator = localState.locator, deviceLabel = currentDeviceLabel, at = localState.lastReadAt,
                                chapterIndex = localState.locator.chapterIndex, chapterCount = publication.chapterCount,
                            ),
                            remote = ReadingPositionSnapshot(
                                locator = remoteState.locator, deviceLabel = remoteDeviceLabel, at = remoteState.lastReadAt,
                                chapterIndex = remoteState.locator.chapterIndex, chapterCount = publication.chapterCount,
                            ),
                        ),
                    )
                    pendingConflictPublicationIds += remoteBackup.publicationId
                }
            }
        }
    }
}
