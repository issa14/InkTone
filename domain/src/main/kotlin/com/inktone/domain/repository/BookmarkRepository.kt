package com.inktone.domain.repository

import com.inktone.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun observeForPublication(publicationId: String): Flow<List<Bookmark>>

    /** Necessaire pour BackupManager (Tache 8.5) — aucune methode globale n'existait avant. */
    fun observeAll(): Flow<List<Bookmark>>
    suspend fun insert(bookmark: Bookmark)
    suspend fun delete(id: String)
}
