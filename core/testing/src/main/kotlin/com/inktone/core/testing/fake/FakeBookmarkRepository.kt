package com.inktone.core.testing.fake

import com.inktone.domain.model.Bookmark
import com.inktone.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeBookmarkRepository : BookmarkRepository {
    private val state = MutableStateFlow<List<Bookmark>>(emptyList())

    override fun observeForPublication(publicationId: String): Flow<List<Bookmark>> =
        state.map { list -> list.filter { it.publicationId == publicationId } }

    override fun observeAll(): Flow<List<Bookmark>> = state

    override suspend fun insert(bookmark: Bookmark) {
        state.value = state.value + bookmark
    }

    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun setPinned(id: String, isPinned: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(isPinned = isPinned) else it }
    }

    override suspend fun updateNote(id: String, note: String?) {
        state.value = state.value.map { if (it.id == id) it.copy(note = note) else it }
    }
}
