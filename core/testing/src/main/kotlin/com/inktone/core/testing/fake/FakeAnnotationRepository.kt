package com.inktone.core.testing.fake

import com.inktone.domain.model.Annotation
import com.inktone.domain.repository.AnnotationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAnnotationRepository : AnnotationRepository {
    private val state = MutableStateFlow<List<Annotation>>(emptyList())

    override fun observeForPublication(publicationId: String): Flow<List<Annotation>> =
        state.map { list -> list.filter { it.publicationId == publicationId } }

    override suspend fun insert(annotation: Annotation) {
        state.value = state.value + annotation
    }

    override suspend fun update(annotation: Annotation) {
        state.value = state.value.map { if (it.id == annotation.id) annotation else it }
    }

    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun setPinned(id: String, isPinned: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(isPinned = isPinned) else it }
    }
}
