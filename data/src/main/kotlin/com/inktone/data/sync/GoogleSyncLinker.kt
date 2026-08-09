package com.inktone.data.sync

import com.inktone.domain.model.SyncAccount
import com.inktone.domain.model.SyncProviderId
import com.inktone.domain.repository.SyncAccountRepository
import javax.inject.Inject

/**
 * Écrit le compte de synchronisation persisté après une authentification
 * Google réussie (tâche 11.6). Vit dans `data` — pas `app` — pour la
 * même raison que `BackupManager` : `app` n'a pas le droit de dépendre
 * de `domain` (Blueprint §12.4), donc rien qui manipule un type
 * `domain` (ici `SyncAccount`) ne peut vivre dans `app`. `app` (seul
 * module qui héberge l'`Activity` d'autorisation, voir
 * `infrastructure/sync`) appelle cette classe avec des types
 * primitifs seulement.
 */
class GoogleSyncLinker @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
) {
    suspend fun link(accountLabel: String) {
        syncAccountRepository.save(
            SyncAccount(provider = SyncProviderId.GOOGLE_DRIVE, accountLabel = accountLabel, linkedAt = System.currentTimeMillis()),
        )
    }

    suspend fun unlink() {
        syncAccountRepository.clear()
    }
}
