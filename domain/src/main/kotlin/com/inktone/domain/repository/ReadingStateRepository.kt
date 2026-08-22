package com.inktone.domain.repository

import com.inktone.domain.model.ReadingState
import kotlinx.coroutines.flow.Flow

interface ReadingStateRepository {
    suspend fun get(publicationId: String): ReadingState?
    fun observe(publicationId: String): Flow<ReadingState?>

    /**
     * Toutes les positions de reprise, en flux.
     *
     * C'est ce qui rend la progression de la Bibliothèque réactive : elle en
     * dérivait auparavant d'un [getAll] ponctuel, rejoué à chaque retour sur
     * l'écran, au prix d'un rechargement visible de la grille entière.
     */
    fun observeAll(): Flow<List<ReadingState>>

    /** Necessaire pour BackupManager (Tache 8.5). */
    suspend fun getAll(): List<ReadingState>

    /** Persiste l'état de reprise. Appelée par les deux chemins K3 (TTS / manuel) — jamais simultanément. */
    suspend fun save(state: ReadingState)
    suspend fun delete(publicationId: String)
}
