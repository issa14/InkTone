package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.Publication
import com.inktone.domain.repository.PublicationRepository
import com.inktone.infrastructure.database.dao.PublicationDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomPublicationRepository @Inject constructor(
    private val dao: PublicationDao,
) : PublicationRepository {
    override fun observeAll(): Flow<List<Publication>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeFiltered(mode: FilterMode, value: String?): Flow<List<Publication>> = when (mode) {
        FilterMode.ALL -> observeAll()
        // TAG/BY_AUTHOR : listes serialisees en colonne (StringListConverter,
        // separateur non imprimable) - pas fiables a matcher en SQL brut. Une
        // seule requete (observeAll) filtree cote Kotlin, pas de N+1 (K8).
        FilterMode.TAG -> dao.observeAll().map { list -> list.filter { value in it.subjects }.map { it.toDomain() } }
        FilterMode.BY_AUTHOR -> dao.observeAll().map { list -> list.filter { value in it.authors }.map { it.toDomain() } }
        else -> dao.observeFiltered(mode.name, value).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getById(id: String): Publication? = dao.getById(id)?.toDomain()
    override suspend fun getByFileHash(hash: String): Publication? = dao.getByFileHash(hash)?.toDomain()
    override suspend fun insert(publication: Publication) = dao.insert(publication.toEntity())
    override suspend fun update(publication: Publication) = dao.update(publication.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun setFavorite(id: String, isFavorite: Boolean) = dao.setFavorite(id, isFavorite)
    override suspend fun setPinned(id: String, isPinned: Boolean) = dao.setPinned(id, isPinned)
    override suspend fun setLastOpened(id: String, lastOpened: Long) = dao.setLastOpened(id, lastOpened)

    // ───── Audit fix : COUNT pour le dashboard ─────
    override suspend fun countFiltered(mode: FilterMode): Int =
        dao.countFiltered(mode.name)
}
