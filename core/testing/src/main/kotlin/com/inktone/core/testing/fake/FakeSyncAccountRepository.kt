package com.inktone.core.testing.fake

import com.inktone.domain.model.SyncAccount
import com.inktone.domain.repository.SyncAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSyncAccountRepository : SyncAccountRepository {
    private val state = MutableStateFlow<SyncAccount?>(null)

    override fun observe(): Flow<SyncAccount?> = state
    override suspend fun get(): SyncAccount? = state.value
    override suspend fun save(account: SyncAccount) {
        state.value = account
    }
    override suspend fun clear() {
        state.value = null
    }
    override suspend fun markSyncSucceeded(at: Long) {
        state.value = state.value?.copy(lastSyncAt = at, lastAutoSyncFailed = false)
    }
    override suspend fun markSyncFailed() {
        state.value = state.value?.copy(lastAutoSyncFailed = true)
    }
}
