package com.inktone.core.testing.fake

import com.inktone.domain.service.SyncOperation
import com.inktone.domain.service.SyncOperationTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSyncOperationTracker : SyncOperationTracker {
    private val state = MutableStateFlow(SyncOperation.NONE)

    override fun observe(): Flow<SyncOperation> = state
    override suspend fun begin(operation: SyncOperation) {
        state.value = operation
    }
    override suspend fun end() {
        state.value = SyncOperation.NONE
    }
}
