package com.inktone.feature.library

import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.Publication
import com.inktone.domain.service.ImportProgress

/**
 * Tache 9bis.4 — tri et recherche titre/auteur en derive du `publications`
 * deja filtre par `PublicationRepository.observeFiltered` (Tache 6.5),
 * pas une nouvelle requete Room : la recherche **dans la bibliotheque**
 * (titre/auteur) est plus simple que la recherche plein texte **dans un
 * livre** (`SearchService`/FTS, Phase 7) - ne pas reutiliser FTS pour un
 * filtrage de titres qui n'en a pas besoin, ne pas confondre les deux.
 */
data class LibraryUiState(
    val publications: List<Publication> = emptyList(),
    val isLoading: Boolean = true,
    val activeFilter: FilterMode = FilterMode.ALL,
    val filterValue: String? = null,
    val searchQuery: String = "",
    val sortOrder: LibrarySortOrder = LibrarySortOrder.RECENTLY_ADDED,
    val layoutMode: LibraryLayoutMode = LibraryLayoutMode.GRID,
    // Tache 6.8 — cache par defaut (total == 0 && !hasQueuedChunks).
    val importProgress: ImportProgress = ImportProgress(),
) {
    /** Tags distincts de la bibliotheque COMPLETE, pas seulement du filtre actif — le drawer doit pouvoir en changer. */
    val availableTags: List<String> get() = publications.flatMap { it.subjects }.distinct().sorted()
    val availableSeries: List<String> get() = publications.mapNotNull { it.seriesName }.distinct().sorted()
    val availableAuthors: List<String> get() = publications.flatMap { it.authors }.distinct().sorted()

    /** Tache 9bis.4 — carte "reprendre la lecture" proeminente, pas seulement un FAB (amelioration legacy). */
    val resumeReadingPublication: Publication?
        get() = publications.filter { it.lastOpened != null }.maxByOrNull { it.lastOpened!! }

    val displayedPublications: List<Publication>
        get() {
            val filtered = if (searchQuery.isBlank()) {
                publications
            } else {
                publications.filter { publication ->
                    publication.title.contains(searchQuery, ignoreCase = true) ||
                        publication.authors.any { it.contains(searchQuery, ignoreCase = true) }
                }
            }
            return when (sortOrder) {
                LibrarySortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
                LibrarySortOrder.RECENTLY_ADDED -> filtered.sortedByDescending { it.importDate }
                LibrarySortOrder.RECENTLY_OPENED -> filtered.sortedByDescending { it.lastOpened ?: 0L }
            }
        }
}

enum class LibrarySortOrder { TITLE, RECENTLY_ADDED, RECENTLY_OPENED }

/** Tâche 1c — 3 dispositions, pas 2 (legacy : Liste / Grille / Grille-couvertures-seules). */
enum class LibraryLayoutMode { LIST, GRID, GRID_COVERS }

fun LibraryLayoutMode.next(): LibraryLayoutMode = when (this) {
    LibraryLayoutMode.LIST -> LibraryLayoutMode.GRID
    LibraryLayoutMode.GRID -> LibraryLayoutMode.GRID_COVERS
    LibraryLayoutMode.GRID_COVERS -> LibraryLayoutMode.LIST
}

sealed interface LibraryIntent {
    data class OpenPublication(val publicationId: String) : LibraryIntent
    data class ToggleFavorite(val publicationId: String, val isFavorite: Boolean) : LibraryIntent
    data class ChangeFilter(val filter: FilterMode, val value: String? = null) : LibraryIntent
    data class SetSearchQuery(val query: String) : LibraryIntent
    data class SetSortOrder(val order: LibrarySortOrder) : LibraryIntent
    data object CycleLayout : LibraryIntent
}

/**
 * Effets ponctuels (Blueprint §4.4 : canal dédié, jamais mélangé à
 * l'état) — consommé par `InkToneNavHost` (Tâche 9bis.2) via
 * `onNavigateToReader`, qui traduit `publicationId` en `ReaderRoute`.
 */
sealed interface LibraryEffect {
    data class NavigateToReader(val publicationId: String) : LibraryEffect
}
