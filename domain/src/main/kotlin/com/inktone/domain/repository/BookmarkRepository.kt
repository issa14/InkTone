package com.inktone.domain.repository

import com.inktone.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun observeForPublication(publicationId: String): Flow<List<Bookmark>>

    /** Necessaire pour BackupManager (Tache 8.5) — aucune methode globale n'existait avant. */
    fun observeAll(): Flow<List<Bookmark>>
    suspend fun insert(bookmark: Bookmark)
    suspend fun delete(id: String)

    /** Lot 4, tâche 4.3 — même patron que Publication.isPinned. */
    suspend fun setPinned(id: String, isPinned: Boolean)

    /**
     * Lot 21, tâche 5 — note optionnelle d'un signet (saisie proposée à la
     * création, jamais obligatoire). `note` peut être `null` (signet sans
     * note) mais la méthode sert à la POSER explicitement.
     */
    suspend fun updateNote(id: String, note: String?)
}
