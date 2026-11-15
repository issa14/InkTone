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

    override fun observeAll(): Flow<List<ReadingState>> = state.map { it.values.toList() }

    override suspend fun getAll(): List<ReadingState> = state.value.values.toList()

    override suspend fun save(state: ReadingState) {
        // Le contexte d'un Locator est hors égalité : une position identique à son
        // contexte près ne ferait pas émettre le StateFlow.
        val updated = this.state.value + (state.publicationId to state)
        this.state.value = emptyMap()
        this.state.value = updated
    }

    override suspend fun delete(publicationId: String) {
        state.value = state.value - publicationId
    }
}
