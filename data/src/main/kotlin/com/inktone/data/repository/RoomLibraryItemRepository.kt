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
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

class RoomLibraryItemRepository @Inject constructor(
    private val dao: LibraryItemDao,
) : LibraryItemRepository {
    /**
     * Sans recherche, filtre, tri et borne restent en SQL. Avec une recherche, SQLite ne sait pas
     * ignorer les accents (« eleve » ne trouve pas « élève », audit UX D66) : la sélection se fait ici,
     * sur la liste triée, puis la borne s'applique au résultat.
     */
    override fun observe(
        filter: LibraryItemFilter,
        searchQuery: String,
        sortOrder: LibraryItemSortOrder,
        limit: Int,
    ): Flow<List<LibraryItem>> {
        val needle = searchQuery.foldedForSearch()
        val searching = needle.isNotEmpty()
        return dao.observe(
            typeFilter = filter.toSqlTypeFilter(),
            searchQuery = "",
            alphabetical = sortOrder == LibraryItemSortOrder.ALPHABETICAL,
            limit = if (searching) Int.MAX_VALUE else limit,
        ).map { list ->
            val items = list.map { it.toDomain() }
            if (searching) items.filter { it.matches(needle) }.take(limit) else items
        }
    }
}

internal fun LibraryItem.matches(foldedNeedle: String): Boolean =
    sequenceOf(excerpt, note, publicationTitle).any { it != null && it.foldedForSearch().contains(foldedNeedle) }

private val combiningMarks = Regex("\\p{Mn}+")

/** Forme de comparaison de la recherche : minuscules, sans accents, sans espaces autour. */
internal fun String.foldedForSearch(): String =
    combiningMarks.replace(Normalizer.normalize(trim(), Normalizer.Form.NFD), "").lowercase(Locale.FRENCH)
