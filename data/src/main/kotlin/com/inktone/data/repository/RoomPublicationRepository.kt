package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toColumn
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.ChapterLengths
import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.Publication
import com.inktone.domain.model.isFinishedAt
import com.inktone.domain.repository.PublicationRepository
import com.inktone.infrastructure.database.dao.PublicationDao
import com.inktone.infrastructure.database.dao.ReadingStateDao
import com.inktone.infrastructure.database.entity.PublicationEntity
import com.inktone.infrastructure.database.entity.ReadingStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomPublicationRepository @Inject constructor(
    private val dao: PublicationDao,
    private val readingStateDao: ReadingStateDao,
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
        // Terminé / En cours suivent la progression affichée, plus l'index de
        // chapitre (audit UX B2, D31) : deux requêtes, filtrées côté Kotlin (K8).
        FilterMode.READ, FilterMode.IN_PROGRESS ->
            combine(dao.observeAll(), readingStateDao.observeAll()) { publications, states ->
                filterByReadingStatus(publications, states, finished = mode == FilterMode.READ)
            }
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
    override suspend fun setCoverUri(id: String, coverUri: String?) = dao.setCoverUri(id, coverUri)

    override suspend fun setFileUri(id: String, fileUri: String) = dao.setFileUri(id, fileUri)

    override suspend fun setLocatorAnchorVersion(id: String, version: Int) = dao.setLocatorAnchorVersion(id, version)
    override suspend fun setChapterLengths(id: String, chapterLengths: ChapterLengths) =
        dao.setChapterLengths(id, chapterLengths.toColumn())
    override suspend fun resetAllCoversToDefault() = dao.resetAllCovers()

    // ───── Audit fix : COUNT pour le dashboard ─────
    override suspend fun countFiltered(mode: FilterMode): Int = when (mode) {
        FilterMode.READ, FilterMode.IN_PROGRESS ->
            filterByReadingStatus(dao.observeAll().first(), readingStateDao.getAll(), finished = mode == FilterMode.READ).size
        else -> dao.countFiltered(mode.name)
    }

    private fun filterByReadingStatus(
        publications: List<PublicationEntity>,
        states: List<ReadingStateEntity>,
        finished: Boolean,
    ): List<Publication> {
        val locators = states.associate { it.publicationId to it.toDomain().locator }
        return publications.mapNotNull { entity ->
            val locator = locators[entity.id] ?: return@mapNotNull null
            val publication = entity.toDomain()
            publication.takeIf { it.isFinishedAt(locator) == finished }
        }
    }
}
