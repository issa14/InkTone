package com.inktone.data.repository

import com.inktone.domain.service.SyncOperation
import com.inktone.domain.service.SyncOperationTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Jamais persisté (tâche 11.2) — un redémarrage de l'app efface toute opération en cours, par construction. */
@Singleton
class InMemorySyncOperationTracker @Inject constructor() : SyncOperationTracker {
    private val state = MutableStateFlow(SyncOperation.NONE)

    override fun observe(): Flow<SyncOperation> = state.asStateFlow()
    override suspend fun begin(operation: SyncOperation) {
        state.value = operation
    }
    override suspend fun end() {
        state.value = SyncOperation.NONE
    }
}
