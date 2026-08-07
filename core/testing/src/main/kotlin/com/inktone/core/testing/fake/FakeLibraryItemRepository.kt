package com.inktone.core.testing.fake

import com.inktone.domain.model.LibraryItem
import com.inktone.domain.model.LibraryItemFilter
import com.inktone.domain.model.LibraryItemSortOrder
import com.inktone.domain.model.LibraryItemType
import com.inktone.domain.repository.LibraryItemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLibraryItemRepository : LibraryItemRepository {
    val state = MutableStateFlow<List<LibraryItem>>(emptyList())

    override fun observe(
        filter: LibraryItemFilter,
        searchQuery: String,
        sortOrder: LibraryItemSortOrder,
    ): Flow<List<LibraryItem>> = state.map { items ->
        items
            .filter { item ->
                when (filter) {
                    LibraryItemFilter.ALL -> true
                    LibraryItemFilter.BOOKMARK -> item.type == LibraryItemType.BOOKMARK
                    LibraryItemFilter.HIGHLIGHT -> item.type == LibraryItemType.HIGHLIGHT
                    LibraryItemFilter.NOTE -> item.type == LibraryItemType.NOTE
                }
            }
            .filter { item ->
                searchQuery.isBlank() ||
                    item.excerpt?.contains(searchQuery, ignoreCase = true) == true ||
                    item.note?.contains(searchQuery, ignoreCase = true) == true ||
                    item.publicationTitle?.contains(searchQuery, ignoreCase = true) == true
            }
            .sortedWith(
                compareByDescending<LibraryItem> { it.isPinned }.let { byPinned ->
                    when (sortOrder) {
                        LibraryItemSortOrder.CHRONOLOGICAL -> byPinned.thenByDescending { it.createdAt }
                        LibraryItemSortOrder.ALPHABETICAL -> byPinned.thenBy { it.publicationTitle ?: "" }
                    }
                },
            )
    }
}
