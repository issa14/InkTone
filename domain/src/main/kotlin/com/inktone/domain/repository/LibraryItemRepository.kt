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
    /**
     * AUDIT_REACTIVITE_UX §6.1 — [limit] borne le nombre d'éléments
     * remontés ; l'appelant l'augmente pour charger la suite (chargement
     * à la demande, pas une requête sans fin par défaut).
     */
    fun observe(
        filter: LibraryItemFilter,
        searchQuery: String,
        sortOrder: LibraryItemSortOrder,
        limit: Int,
    ): Flow<List<LibraryItem>>
}
