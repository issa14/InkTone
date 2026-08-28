package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toSqlTypeFilter
import com.inktone.domain.model.LibraryItem
import com.inktone.domain.model.LibraryItemFilter
import com.inktone.domain.model.LibraryItemSortOrder
import com.inktone.domain.repository.LibraryItemRepository
import com.inktone.infrastructure.database.dao.LibraryItemDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomLibraryItemRepository @Inject constructor(
    private val dao: LibraryItemDao,
) : LibraryItemRepository {
    override fun observe(
        filter: LibraryItemFilter,
        searchQuery: String,
        sortOrder: LibraryItemSortOrder,
        limit: Int,
    ): Flow<List<LibraryItem>> = dao.observe(
        typeFilter = filter.toSqlTypeFilter(),
        searchQuery = searchQuery,
        alphabetical = sortOrder == LibraryItemSortOrder.ALPHABETICAL,
        limit = limit,
    ).map { list -> list.map { it.toDomain() } }
}
