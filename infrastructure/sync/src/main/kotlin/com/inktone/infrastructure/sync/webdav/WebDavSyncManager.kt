package com.inktone.infrastructure.sync.webdav

import com.inktone.domain.model.SyncAccount
import com.inktone.domain.model.SyncProviderId
import com.inktone.domain.repository.SyncAccountRepository
import com.inktone.domain.service.SyncOperationResult
import com.inktone.domain.service.WebDavSyncService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémente [WebDavSyncService] (Lot 19) — orchestre la connexion
 * WebDAV : test via [WebDavSyncProvider], puis persistance des
 * identifiants (chiffrés) et du compte (`accountLabel` = hôte de l'URL).
 * L'exclusivité mutuelle WebDAV/Google Drive est appliquée par
 * [SyncAccountRepository.save] (remplace tout compte existant), en
 * complément du grisage conditionnel côté UI.
 */
@Singleton
class WebDavSyncManager @Inject constructor(
    private val webDavSyncProvider: WebDavSyncProvider,
    private val credentialsStore: WebDavCredentialsStoreContract,
    private val syncAccountRepository: SyncAccountRepository,
) : WebDavSyncService {

    override suspend fun testConnection(url: String, username: String, password: String): SyncOperationResult =
        webDavSyncProvider.testConnection(url, username, password)

    override suspend fun connect(url: String, username: String, password: String): SyncOperationResult {
        val result = webDavSyncProvider.testConnection(url, username, password)
        if (result is SyncOperationResult.Success) {
            credentialsStore.write(WebDavCredentials(url = url, username = username, password = password))
            syncAccountRepository.save(
                SyncAccount(
                    provider = SyncProviderId.WEBDAV,
                    accountLabel = hostOf(url),
                    linkedAt = System.currentTimeMillis(),
                ),
            )
        }
        return result
    }

    override suspend fun disconnect() {
        credentialsStore.clear()
        // Ne jamais effacer un compte Google Drive actif : le grisage
        // mutuel empêche d'arriver ici avec Drive actif, mais ce garde
        // reste une source de vérité indépendante de l'UI.
        if (syncAccountRepository.get()?.provider == SyncProviderId.WEBDAV) {
            syncAccountRepository.clear()
        }
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)
}
