package com.inktone.infrastructure.sync

import com.inktone.domain.model.SyncProviderId
import com.inktone.domain.repository.SyncAccountRepository
import com.inktone.domain.service.SyncOperationResult
import com.inktone.domain.service.SyncProvider
import com.inktone.domain.service.SyncRemoteFile
import com.inktone.infrastructure.sync.di.GoogleDriveProvider
import com.inktone.infrastructure.sync.di.WebDavProvider
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
    @GoogleDriveProvider private val googleDriveSyncProvider: SyncProvider,
    @WebDavProvider private val webDavSyncProvider: SyncProvider,
    private val syncAccountRepository: SyncAccountRepository,
) : SyncProvider {

    /**
     * Non consommé par la production : le routeur sélectionne le délégué
     * à chaque opération suspendue via le compte persisté. Valeur
     * documentaire (interface [SyncProvider]), pas une source de vérité.
     */
    override val id: SyncProviderId = SyncProviderId.GOOGLE_DRIVE

    private suspend fun delegate(): SyncProvider =
        if (syncAccountRepository.get()?.provider == SyncProviderId.WEBDAV) webDavSyncProvider else googleDriveSyncProvider

    override suspend fun upload(fileName: String, bytes: ByteArray): SyncOperationResult =
        delegate().upload(fileName, bytes)

    override suspend fun download(fileName: String): ByteArray? = delegate().download(fileName)

    override suspend fun list(): List<SyncRemoteFile> = delegate().list()

    override suspend fun delete(fileName: String): SyncOperationResult = delegate().delete(fileName)
}
