package com.inktone.core.testing.fake

import com.inktone.domain.model.Publication
import com.inktone.domain.repository.PublicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePublicationRepository : PublicationRepository {
    private val state = MutableStateFlow<List<Publication>>(emptyList())

    override fun observeAll(): Flow<List<Publication>> = state

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
