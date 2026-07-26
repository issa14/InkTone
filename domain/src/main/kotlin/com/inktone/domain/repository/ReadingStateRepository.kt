package com.inktone.domain.repository

import com.inktone.domain.model.ReadingState
import kotlinx.coroutines.flow.Flow

interface ReadingStateRepository {
    suspend fun get(publicationId: String): ReadingState?
    fun observe(publicationId: String): Flow<ReadingState?>

    /** Persiste l'état de reprise. Appelée par les deux chemins K3 (TTS / manuel) — jamais simultanément. */
    suspend fun save(state: ReadingState)
    suspend fun delete(publicationId: String)
}
