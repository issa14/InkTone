package com.inktone.data.sync

import com.inktone.domain.service.SyncOperationResult
import com.inktone.domain.service.WebDavSyncService
import javax.inject.Inject

/** Résultat de connexion WebDAV exprimé sans type `domain` (Blueprint §12.4 : `app` n'a pas le droit de dépendre de `domain`). */
sealed interface WebDavSyncLinkResult {
    data object Success : WebDavSyncLinkResult
    data class Failed(val message: String) : WebDavSyncLinkResult
}

/**
 * Pont entre `infrastructure/sync` ([WebDavSyncService], réseau +
 * identifiants) et `app` (`SyncAuthViewModel`) — même rôle que
 * [GoogleSyncLinker] : `app` appelle avec des types primitifs et reçoit
 * un résultat primitif, jamais un type `domain` (Lot 19).
 */
class WebDavSyncLinker @Inject constructor(
    private val webDavSyncService: WebDavSyncService,
) {
    suspend fun connect(url: String, username: String, password: String): WebDavSyncLinkResult =
        when (val result = webDavSyncService.connect(url, username, password)) {
            is SyncOperationResult.Success -> WebDavSyncLinkResult.Success
            is SyncOperationResult.Failed -> WebDavSyncLinkResult.Failed(result.message)
        }

    suspend fun disconnect() = webDavSyncService.disconnect()
}
