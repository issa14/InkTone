package com.inktone.core.testing.fake

import com.inktone.domain.model.ReadingState
import com.inktone.domain.repository.ReadingStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeReadingStateRepository : ReadingStateRepository {
    private val state = MutableStateFlow<Map<String, ReadingState>>(emptyMap())

    override suspend fun get(publicationId: String): ReadingState? = state.value[publicationId]

    override fun observe(publicationId: String): Flow<ReadingState?> =
        state.map { it[publicationId] }

    override suspend fun save(state: ReadingState) {
        this.state.value = this.state.value + (state.publicationId to state)
    }

    override suspend fun delete(publicationId: String) {
        state.value = state.value - publicationId
    }
}
