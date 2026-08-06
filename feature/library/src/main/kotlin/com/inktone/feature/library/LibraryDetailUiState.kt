package com.inktone.feature.library

import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat

/**
 * Lot 2a.4 — `feature:library` n'a pas le plugin kotlinx.serialization
 * (seuls `app`/`data` l'ont) : `LibraryDetailRoute` (`app/Routes.kt`)
 * porte donc la catégorie en `String` brute, traduite en
 * [LibraryDetailCategory] côté `InkToneNavHost` — même principe que
 * `ReaderRoute` qui n'expose que des primitifs à travers la frontière
 * app/domaine (Blueprint §12.4).
 */
enum class LibraryDetailCategory { SERIES, TAG }

/**
 * Écran de détail Séries/Tags (UX §Menu déroulant du titre, écran de
 * détail partagé) — un seul écran réutilisable pour les deux cas.
 * Toujours en vue Liste (décision de la cible, pas de bascule de
 * disposition ici) — `filterAndSort` est partagé avec [LibraryUiState].
 */
data class LibraryDetailUiState(
    val category: LibraryDetailCategory? = null,
    val value: String = "",
    val publications: List<Publication> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val sortOrder: LibrarySortOrder = LibrarySortOrder.RECENTLY_ADDED,
    val selectedFormats: Set<PublicationFormat> = emptySet(),
    val progressMap: Map<String, Int> = emptyMap(),
) {
    val displayedPublications: List<Publication>
        get() = publications.filterAndSort(searchQuery, selectedFormats, sortOrder)
}

sealed interface LibraryDetailIntent {
    data class Load(val category: LibraryDetailCategory, val value: String) : LibraryDetailIntent
    data class OpenPublication(val publicationId: String) : LibraryDetailIntent
    data class ToggleFavorite(val publicationId: String, val isFavorite: Boolean) : LibraryDetailIntent
    data class TogglePin(val publicationId: String, val isPinned: Boolean) : LibraryDetailIntent
    data class DeletePublication(val publicationId: String) : LibraryDetailIntent
    data class SetSearchQuery(val query: String) : LibraryDetailIntent
    data class SetSortOrder(val order: LibrarySortOrder) : LibraryDetailIntent
    data class ToggleFileFormat(val format: PublicationFormat) : LibraryDetailIntent
    data object ClearFileFormats : LibraryDetailIntent
}

sealed interface LibraryDetailEffect {
    data class NavigateToReader(val publicationId: String) : LibraryDetailEffect
}
