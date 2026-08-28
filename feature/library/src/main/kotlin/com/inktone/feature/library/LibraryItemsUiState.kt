package com.inktone.feature.library

import com.inktone.domain.model.LibraryItem
import com.inktone.domain.model.LibraryItemFilter
import com.inktone.domain.model.LibraryItemSortOrder

/**
 * Lot 4, tâche 4.6 — reconstruction de la vue globale « Marque-pages et
 * notes » (remplace `GlobalBookmarksUiState`, signets seuls). Filtre,
 * recherche et tri ne sont PAS appliqués ici : [items] est déjà le
 * résultat filtré/trié par [com.inktone.domain.repository.LibraryItemRepository.observe]
 * (tâche 4.4, requête SQL) — jamais un second filtrage en mémoire.
 */
data class LibraryItemsUiState(
    val items: List<LibraryItem> = emptyList(),
    val searchQuery: String = "",
    val isSearchExpanded: Boolean = false,
    val filter: LibraryItemFilter = LibraryItemFilter.ALL,
    val sortOrder: LibraryItemSortOrder = LibraryItemSortOrder.CHRONOLOGICAL,
    val isLoading: Boolean = true,
    /** Élément en attente de confirmation de suppression (balayage) — aucune suppression n'est immédiate (tâche 4.6). */
    val pendingDelete: LibraryItem? = null,
    // AUDIT_REACTIVITE_UX §6.1 — borne de la requête (LibraryItemRepository.observe),
    // relevée par LoadMore quand l'utilisateur approche du bas de la liste.
    val visibleLimit: Int = LIBRARY_ITEMS_PAGE_SIZE,
) {
    /**
     * `items.size == visibleLimit` : la requête a peut-être encore des
     * lignes au-delà de la borne (ou s'arrête pile dessus par coïncidence
     * — un LoadMore de plus le confirmera sans effet visible). Moins que
     * `visibleLimit` : la requête a tout remonté, plus rien à charger.
     */
    val canLoadMore: Boolean get() = items.size >= visibleLimit
}

internal const val LIBRARY_ITEMS_PAGE_SIZE = 100

sealed interface LibraryItemsIntent {
    data class SetSearchQuery(val query: String) : LibraryItemsIntent
    data object ToggleSearchExpanded : LibraryItemsIntent
    data class SetFilter(val filter: LibraryItemFilter) : LibraryItemsIntent
    data class SetSortOrder(val sortOrder: LibraryItemSortOrder) : LibraryItemsIntent
    data class OpenItem(val item: LibraryItem) : LibraryItemsIntent
    data class RequestDelete(val item: LibraryItem) : LibraryItemsIntent
    data object ConfirmDelete : LibraryItemsIntent
    data object CancelDelete : LibraryItemsIntent
    data class TogglePin(val item: LibraryItem) : LibraryItemsIntent
    data object LoadMore : LibraryItemsIntent
}

sealed interface LibraryItemsEffect {
    data class NavigateToReader(
        val publicationId: String,
        val resourceHref: String,
        val chapterIndex: Int,
        val charOffset: Int,
    ) : LibraryItemsEffect
}
