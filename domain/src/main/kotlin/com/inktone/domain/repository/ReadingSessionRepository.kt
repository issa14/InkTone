package com.inktone.domain.repository

import com.inktone.domain.model.ReadingSession

interface ReadingSessionRepository {
    suspend fun insert(session: ReadingSession)
    suspend fun getAllForPublication(publicationId: String): List<ReadingSession>
    suspend fun getAll(): List<ReadingSession>
}
