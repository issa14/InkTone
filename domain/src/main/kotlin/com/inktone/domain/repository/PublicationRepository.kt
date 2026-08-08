package com.inktone.domain.repository

import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.Publication
import kotlinx.coroutines.flow.Flow

interface PublicationRepository {
    fun observeAll(): Flow<List<Publication>>

    /**
     * Filtre la bibliothèque (Tâche 6.5). [value] est ignoré sauf pour
     * [FilterMode.SERIES] (nom de série exact), [FilterMode.TAG] (sujet
     * exact) et [FilterMode.BY_AUTHOR] (nom d'auteur exact).
     */
    fun observeFiltered(mode: FilterMode, value: String? = null): Flow<List<Publication>>
    suspend fun getById(id: String): Publication?
    suspend fun getByFileHash(hash: String): Publication?
    suspend fun insert(publication: Publication)
    suspend fun update(publication: Publication)
    suspend fun delete(id: String)
    suspend fun setFavorite(id: String, isFavorite: Boolean)
    suspend fun setPinned(id: String, isPinned: Boolean)

    // ───── Audit fix : COUNT pour le dashboard (pas .first().size) ─────
    suspend fun countFiltered(mode: FilterMode): Int
}
