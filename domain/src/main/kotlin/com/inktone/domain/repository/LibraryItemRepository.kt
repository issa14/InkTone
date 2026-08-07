package com.inktone.domain.repository

import com.inktone.domain.model.LibraryItem
import com.inktone.domain.model.LibraryItemFilter
import com.inktone.domain.model.LibraryItemSortOrder
import kotlinx.coroutines.flow.Flow

/**
 * Vue globale marque-pages + annotations, tous ouvrages confondus (Lot 4).
 *
 * La recherche, le filtrage et le tri s'exécutent au niveau de la
 * requête (tâche 4.4) — jamais par [kotlin.collections.filter] sur une
 * liste déjà chargée en mémoire, pour rester réactif indépendamment du
 * volume de données.
 */
interface LibraryItemRepository {
    fun observe(
        filter: LibraryItemFilter,
        searchQuery: String,
        sortOrder: LibraryItemSortOrder,
    ): Flow<List<LibraryItem>>
}
