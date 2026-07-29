package com.inktone.core.testing.fake

import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.Publication
import com.inktone.domain.repository.PublicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class FakePublicationRepository : PublicationRepository {
    private val state = MutableStateFlow<List<Publication>>(emptyList())

    // Simule la jointure reading_states de RoomPublicationRepository
    // (Tache 6.5) sans reimplementer ReadingStateRepository ici — un
    // chapterIndex par publication, absent = UNREAD.
    private val readingProgress = MutableStateFlow<Map<String, Int>>(emptyMap())

    fun setChapterProgress(publicationId: String, chapterIndex: Int) {
        readingProgress.value = readingProgress.value + (publicationId to chapterIndex)
    }

    override fun observeAll(): Flow<List<Publication>> = state

    override fun observeFiltered(mode: FilterMode, value: String?): Flow<List<Publication>> =
        combine(state, readingProgress) { publications, progress ->
            when (mode) {
                FilterMode.ALL -> publications
                FilterMode.FAVORITES -> publications.filter { it.isFavorite }
                FilterMode.SERIES -> publications.filter { it.seriesName == value }
                FilterMode.TAG -> publications.filter { value in it.subjects }
                FilterMode.BY_AUTHOR -> publications.filter { value in it.authors }
                FilterMode.UNREAD -> publications.filter { it.id !in progress }
                FilterMode.IN_PROGRESS -> publications.filter { pub ->
                    val chapterIndex = progress[pub.id] ?: return@filter false
                    chapterIndex < pub.chapterCount - 1
                }
                FilterMode.READ -> publications.filter { pub ->
                    val chapterIndex = progress[pub.id] ?: return@filter false
                    chapterIndex >= pub.chapterCount - 1
                }
            }
        }

    override suspend fun getById(id: String): Publication? =
        state.value.firstOrNull { it.id == id }

    override suspend fun getByFileHash(hash: String): Publication? =
        state.value.firstOrNull { it.fileHash == hash }

    override suspend fun insert(publication: Publication) {
        state.value = state.value + publication
    }

    override suspend fun update(publication: Publication) {
        state.value = state.value.map { if (it.id == publication.id) publication else it }
    }

    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun setFavorite(id: String, isFavorite: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(isFavorite = isFavorite) else it }
    }
}
