package com.inktone.core.testing.fake

import com.inktone.domain.model.ReadingSession
import com.inktone.domain.repository.ReadingSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeReadingSessionRepository : ReadingSessionRepository {
    private val state = MutableStateFlow<List<ReadingSession>>(emptyList())

    override suspend fun insert(session: ReadingSession) {
        state.value = state.value + session
    }

    override suspend fun getAllForPublication(publicationId: String): List<ReadingSession> =
        state.value.filter { it.publicationId == publicationId }

    override suspend fun getAll(): List<ReadingSession> = state.value
}
