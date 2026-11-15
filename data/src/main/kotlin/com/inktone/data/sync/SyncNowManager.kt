package com.inktone.data.sync

import com.inktone.data.backup.BackupManager
import com.inktone.data.backup.BackupPayload
import com.inktone.data.backup.PublicationMatcher
import com.inktone.data.backup.backupJson
import com.inktone.data.backup.toDomain
import com.inktone.domain.model.DeviceFleetEntry
import com.inktone.domain.model.PositionConflict
import com.inktone.domain.model.ReadingPositionSnapshot
import com.inktone.domain.model.SyncActivityEvent
import com.inktone.domain.model.SyncActivityEventType
import com.inktone.domain.model.SyncTombstone
import com.inktone.domain.model.TombstoneKind
import com.inktone.domain.repository.AnnotationRepository
import com.inktone.domain.repository.BookmarkRepository
import com.inktone.domain.repository.ConflictQueueRepository
import com.inktone.domain.repository.DeviceIdentityRepository
import com.inktone.domain.repository.PronunciationRuleRepository
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingSessionRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.repository.SyncAccountRepository
import com.inktone.domain.repository.SyncActivityLogRepository
import com.inktone.domain.repository.SyncFleetRepository
import com.inktone.domain.repository.SyncTombstoneRepository
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

/** Durée de conservation d'une tombe : au-delà, tous les appareils ont eu le temps de la recevoir (180 jours). */
private const val TOMBSTONE_RETENTION_MS = 180L * 24 * 60 * 60 * 1000

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
 * **Suppressions et modifications (audit UX D61)** : chaque suppression de signet ou
 * d'annotation laisse une tombe horodatée, transportée par l'instantané. À la fusion :
 * - une tombe supprime l'élément local, sauf s'il a été modifié après la suppression ;
 * - un élément distant n'est pas recréé si une tombe locale est plus récente que sa dernière
 *   modification (il serait revenu de l'instantané d'un autre appareil) ;
 * - un élément déjà connu prend la version la plus récente (`updatedAt`), la plus récente gagne.
 * Les tombes reçues sont gardées pour être relayées, et purgées après [TOMBSTONE_RETENTION_MS].
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
    private val readingSessionRepository: ReadingSessionRepository,
    private val readingStateRepository: ReadingStateRepository,
    private val publicationRepository: PublicationRepository,
    private val conflictQueueRepository: ConflictQueueRepository,
    private val syncTombstoneRepository: SyncTombstoneRepository,
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
        val localBookmarks = bookmarkRepository.observeAll().first().associateBy { it.id }.toMutableMap()
        val localAnnotations = annotationRepository.getAll().associateBy { it.id }.toMutableMap()
        val localTombstones = syncTombstoneRepository.getAll().associateBy { it.id }.toMutableMap()
        // Lot 22, tâche 13 — union des sessions par clé primaire (id stable),
        // idempotente par construction : on ne réinsère jamais un id déjà
        // local, d'où aucune agrégation à écrire.
        val localSessionIds = readingSessionRepository.getAll().map { it.id }.toMutableSet()
        val pendingConflictPublicationIds = conflictQueueRepository.listPending().map { it.publicationId }.toMutableSet()
        var skippedOrphanSessions = 0
        // Lot 22, tâche 14 — dernière synchro réussie, repère de l'arbitrage
        // automatique : on ne tranche seul que si UN SEUL appareil a bougé
        // depuis ce moment (décision 5).
        val lastSyncAt = syncAccountRepository.get()?.lastSyncAt ?: 0L

        for (file in remoteFiles) {
            val bytes = syncProvider.download(file.name) ?: continue
            val payload = runCatching { backupJson.decodeFromString(BackupPayload.serializer(), bytes.decodeToString()) }.getOrNull() ?: continue
            val remoteDeviceId = file.name.removePrefix(SNAPSHOT_PREFIX).removeSuffix(SNAPSHOT_SUFFIX)
            val remoteDeviceLabel = fleetLabels[remoteDeviceId] ?: remoteDeviceId
            // Les livres de l'autre appareil n'ont pas nos identifiants : ils
            // sont retrouvés par l'empreinte de leur fichier (audit UX D57).
            val matcher = PublicationMatcher(payload, publicationRepository)

            // Suppressions d'abord : elles s'appliquent avant que les éléments de cet instantané
            // ne soient recréés (audit UX D61).
            payload.deletions.mapNotNull { it.toDomain() }.forEach { tombstone ->
                val known = localTombstones[tombstone.id]
                if (known == null || known.deletedAt < tombstone.deletedAt) {
                    syncTombstoneRepository.record(tombstone)
                    localTombstones[tombstone.id] = tombstone
                }
                when (tombstone.kind) {
                    TombstoneKind.BOOKMARK -> localBookmarks[tombstone.id]?.let { local ->
                        // Modifié après sa suppression ailleurs : la modification l'emporte.
                        if (local.updatedAt <= tombstone.deletedAt) {
                            bookmarkRepository.delete(tombstone.id)
                            localBookmarks.remove(tombstone.id)
                        }
                    }
                    TombstoneKind.ANNOTATION -> localAnnotations[tombstone.id]?.let { local ->
                        if (local.updatedAt <= tombstone.deletedAt) {
                            annotationRepository.delete(tombstone.id)
                            localAnnotations.remove(tombstone.id)
                        }
                    }
                }
            }

            // Signets et annotations : jamais de question posée. Un élément supprimé ne revient pas ;
            // un élément déjà connu prend la version la plus récente.
            payload.bookmarks.forEach { backup ->
                val remote = backup.toDomain()
                if (isDeletedSince(localTombstones[backup.id], remote.updatedAt)) return@forEach
                val local = localBookmarks[backup.id]
                if (local != null) {
                    if (remote.updatedAt > local.updatedAt) {
                        val merged = local.copy(title = remote.title, note = remote.note, updatedAt = remote.updatedAt)
                        bookmarkRepository.update(merged)
                        localBookmarks[backup.id] = merged
                    }
                    return@forEach
                }
                val publicationId = matcher.localIdOf(backup.publicationId) ?: return@forEach
                forgetTombstone(localTombstones, backup.id)
                val inserted = remote.copy(publicationId = publicationId)
                bookmarkRepository.insert(inserted)
                localBookmarks[backup.id] = inserted
            }
            payload.annotations.forEach { backup ->
                val remote = backup.toDomain()
                if (isDeletedSince(localTombstones[backup.id], remote.updatedAt)) return@forEach
                val local = localAnnotations[backup.id]
                if (local != null) {
                    if (remote.updatedAt > local.updatedAt) {
                        val merged = local.copy(
                            color = remote.color, kind = remote.kind, content = remote.content,
                            excerpt = remote.excerpt, updatedAt = remote.updatedAt,
                        )
                        annotationRepository.update(merged)
                        localAnnotations[backup.id] = merged
                    }
                    return@forEach
                }
                val publicationId = matcher.localIdOf(backup.publicationId) ?: return@forEach
                forgetTombstone(localTombstones, backup.id)
                val inserted = remote.copy(publicationId = publicationId)
                annotationRepository.insert(inserted)
                localAnnotations[backup.id] = inserted
            }
            // Réglages/thèmes : dernier écrit gagne (REPLACE au niveau DAO).
            payload.pronunciationRules.forEach { pronunciationRuleRepository.save(it.toDomain()) }
            payload.customThemes.forEach { themeRepository.saveCustom(it.toDomain()) }

            // Lot 22, tâche 13 — sessions de lecture : union par identifiant.
            // Une session dont la publication est absente en local est IGNORÉE
            // et journalisée (décision 6), jamais insérée — insérer violerait
            // la clé étrangère CASCADE vers publications. Ne jamais conflater
            // ReadingSession (historique, N par livre) et ReadingState
            // (position, 1 par livre).
            payload.readingSessions.forEach { backup ->
                if (backup.id in localSessionIds) return@forEach
                val publicationId = matcher.localIdOf(backup.publicationId)
                if (publicationId == null) {
                    skippedOrphanSessions++
                    return@forEach
                }
                readingSessionRepository.insert(backup.toDomain().copy(publicationId = publicationId))
                localSessionIds += backup.id
            }

            // Position de lecture — jamais fusionnée à l'aveugle. Décision 5 :
            // arbitrage automatique seulement si un seul appareil a bougé
            // depuis la dernière synchro réussie ; sinon, choix explicite via
            // SyncConflictBottomSheet (file PendingConflictEntity).
            payload.readingStates.forEach { remoteBackup ->
                val publicationId = matcher.localIdOf(remoteBackup.publicationId) ?: return@forEach
                if (publicationId in pendingConflictPublicationIds) return@forEach
                val publication = publicationRepository.getById(publicationId) ?: return@forEach
                val remoteState = remoteBackup.toDomain().copy(publicationId = publicationId)
                val localState = readingStateRepository.get(publicationId)

                if (localState == null) {
                    readingStateRepository.save(remoteState)
                } else if (localState.locator != remoteState.locator) {
                    val localMoved = localState.lastReadAt > lastSyncAt
                    val remoteMoved = remoteState.lastReadAt > lastSyncAt
                    when {
                        // Seul le local a bougé : il fait foi, rien à changer.
                        localMoved && !remoteMoved -> Unit
                        // Seul le distant a bougé : on l'adopte sans demander.
                        !localMoved && remoteMoved -> readingStateRepository.save(remoteState)
                        // Les deux ont bougé (ou ni l'un ni l'autre) : conflit réel.
                        else -> {
                            conflictQueueRepository.enqueue(
                                PositionConflict(
                                    publicationId = publicationId,
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
                            pendingConflictPublicationIds += publicationId
                        }
                    }
                }
            }
        }

        // Les tombes assez anciennes ont eu le temps de se propager à tous les appareils.
        syncTombstoneRepository.purgeBefore(System.currentTimeMillis() - TOMBSTONE_RETENTION_MS)

        // Journalise les sessions orphelines ignorées — jamais silencieux
        // (décision 6), mais en un seul événement pour ne pas saturer le
        // journal d'une synchro à l'autre.
        if (skippedOrphanSessions > 0) {
            syncActivityLogRepository.appendEvent(
                SyncActivityEvent(
                    UUID.randomUUID().toString(),
                    SyncActivityEventType.SUCCESS,
                    "$skippedOrphanSessions session(s) de lecture ignorée(s) : livre absent localement",
                    System.currentTimeMillis(),
                ),
            )
        }
    }
    /** Vrai si [tombstone] supprime un élément dont la dernière modification ([updatedAt]) ne la dépasse pas. */
    private fun isDeletedSince(tombstone: SyncTombstone?, updatedAt: Long): Boolean =
        tombstone != null && tombstone.deletedAt >= updatedAt

    /** Un élément modifié après sa suppression ailleurs est recréé : sa tombe n'a plus lieu d'être. */
    private suspend fun forgetTombstone(localTombstones: MutableMap<String, SyncTombstone>, id: String) {
        if (localTombstones.remove(id) != null) syncTombstoneRepository.remove(id)
    }
}
