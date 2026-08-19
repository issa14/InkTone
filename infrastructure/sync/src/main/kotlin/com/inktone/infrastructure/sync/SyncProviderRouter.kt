package com.inktone.infrastructure.sync

import com.inktone.domain.model.SyncProviderId
import com.inktone.domain.repository.SyncAccountRepository
import com.inktone.domain.service.SyncOperationResult
import com.inktone.domain.service.SyncProvider
import com.inktone.domain.service.SyncRemoteFile
import com.inktone.infrastructure.sync.drive.GoogleDriveSyncProvider
import com.inktone.infrastructure.sync.webdav.WebDavSyncProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aiguille [SyncProvider] vers le fournisseur actif (Lot 19) — remplace
 * le binding direct sur Google Drive (tâche 11.5) par une sélection par
 * [com.inktone.domain.model.SyncProviderId] lue depuis le compte
 * persisté. Sans compte, le repli est Google Drive (seul fournisseur
 * pouvant être lié avant une connexion WebDAV).
 */
@Singleton
class SyncProviderRouter @Inject constructor(
    private val googleDriveSyncProvider: GoogleDriveSyncProvider,
    private val webDavSyncProvider: WebDavSyncProvider,
    private val syncAccountRepository: SyncAccountRepository,
) : SyncProvider {

    @Volatile
    private var activeId: SyncProviderId = SyncProviderId.GOOGLE_DRIVE

    override val id: SyncProviderId get() = activeId

    private suspend fun delegate(): SyncProvider {
        val provider = syncAccountRepository.get()?.provider ?: SyncProviderId.GOOGLE_DRIVE
        activeId = provider
        return if (provider == SyncProviderId.WEBDAV) webDavSyncProvider else googleDriveSyncProvider
    }

    override suspend fun upload(fileName: String, bytes: ByteArray): SyncOperationResult =
        delegate().upload(fileName, bytes)

    override suspend fun download(fileName: String): ByteArray? = delegate().download(fileName)

    override suspend fun list(): List<SyncRemoteFile> = delegate().list()

    override suspend fun delete(fileName: String): SyncOperationResult = delegate().delete(fileName)
}
