package com.inktone.core.testing.fake

import com.inktone.domain.service.SyncNowService
import com.inktone.domain.service.SyncOperationResult

/** Faux `SyncNowService` pour les tests de ViewModel — configurable, succès par défaut. */
class FakeSyncNowService(
    private var result: SyncOperationResult = SyncOperationResult.Success,
) : SyncNowService {
    var callCount = 0
        private set

    fun setNextResult(result: SyncOperationResult) {
        this.result = result
    }

    override suspend fun synchronizeNow(): SyncOperationResult {
        callCount++
        return result
    }
}
