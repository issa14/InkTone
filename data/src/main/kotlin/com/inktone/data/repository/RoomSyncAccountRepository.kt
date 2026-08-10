package com.inktone.data.repository

import com.inktone.domain.model.SyncAccount
import com.inktone.domain.model.SyncProviderId
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.repository.SyncAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Le compte de synchronisation est un sous-ensemble de champs de
 * `UserPreferences` (ligne unique) plutôt qu'une table dédiée — même
 * convention que `hasSeenOnboarding`/`libraryLayoutMode` : un singleton
 * de plus sur la même ligne, pas une nouvelle table Room pour cinq
 * colonnes nullables (tâche 11.2).
 */
class RoomSyncAccountRepository @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : SyncAccountRepository {

    override fun observe(): Flow<SyncAccount?> = preferencesRepository.observe().map { it.toSyncAccount() }

    override suspend fun get(): SyncAccount? = preferencesRepository.get().toSyncAccount()

    override suspend fun save(account: SyncAccount) {
        val current = preferencesRepository.get()
        preferencesRepository.update(
            current.copy(
                syncProvider = account.provider.name,
                syncAccountLabel = account.accountLabel,
                syncLinkedAt = account.linkedAt,
                syncLastSyncAt = account.lastSyncAt,
                syncLastAutoSyncFailed = account.lastAutoSyncFailed,
            ),
        )
    }

    override suspend fun clear() {
        val current = preferencesRepository.get()
        preferencesRepository.update(
            current.copy(
                syncProvider = null, syncAccountLabel = null, syncLinkedAt = null,
                syncLastSyncAt = null, syncLastAutoSyncFailed = false,
            ),
        )
    }

    override suspend fun markSyncSucceeded(at: Long) {
        val current = preferencesRepository.get()
        preferencesRepository.update(current.copy(syncLastSyncAt = at, syncLastAutoSyncFailed = false))
    }

    override suspend fun markSyncFailed() {
        val current = preferencesRepository.get()
        preferencesRepository.update(current.copy(syncLastAutoSyncFailed = true))
    }

    private fun com.inktone.domain.model.UserPreferences.toSyncAccount(): SyncAccount? {
        val provider = syncProvider ?: return null
        val label = syncAccountLabel ?: return null
        val linkedAt = syncLinkedAt ?: return null
        return SyncAccount(
            provider = SyncProviderId.valueOf(provider),
            accountLabel = label,
            linkedAt = linkedAt,
            lastSyncAt = syncLastSyncAt,
            lastAutoSyncFailed = syncLastAutoSyncFailed,
        )
    }
}
