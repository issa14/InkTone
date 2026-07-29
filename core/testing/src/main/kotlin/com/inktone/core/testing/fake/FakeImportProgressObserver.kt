package com.inktone.core.testing.fake

import com.inktone.domain.service.ImportProgress
import com.inktone.domain.service.ImportProgressObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeImportProgressObserver(
    initial: ImportProgress = ImportProgress(),
) : ImportProgressObserver {
    private val state = MutableStateFlow(initial)

    fun emit(progress: ImportProgress) {
        state.value = progress
    }

    override fun observe(): Flow<ImportProgress> = state
}
