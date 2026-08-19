package com.inktone.feature.library

import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.service.ImportProgress
import com.inktone.domain.service.ImportResultEntry
import com.inktone.domain.usecase.CoverRegenerationResult

/**
 * Tache 9bis.4 — tri et recherche titre/auteur en derive du `publications`
 * deja filtre par `PublicationRepository.observeFiltered` (Tache 6.5),
 * pas une nouvelle requete Room : la recherche **dans la bibliotheque**
 * (titre/auteur) est plus simple que la recherche plein texte **dans un
 * livre** (`SearchService`/FTS, Phase 7) - ne pas reutiliser FTS pour un
 * filtrage de titres qui n'en a pas besoin, ne pas confondre les deux.
 *
 * `selectedFormats` (Lot 2a.1) suit le meme principe : filtre client-cote,
 * pas une nouvelle requete. Ensemble vide = aucun filtre (« Tous »),
 * cohabite avec `activeFilter`/`filterValue` qui restent le filtre
 * serveur (statut, serie, tag, auteur).
 */
data class LibraryUiState(
    val publications: List<Publication> = emptyList(),
    val isLoading: Boolean = true,
    val activeFilter: FilterMode = FilterMode.ALL,
    val filterValue: String? = null,
    val searchQuery: String = "",
    val sortOrder: LibrarySortOrder = LibrarySortOrder.RECENTLY_ADDED,
    val layoutMode: LibraryLayoutMode = LibraryLayoutMode.GRID_COVERS,
    val selectedFormats: Set<PublicationFormat> = emptySet(),
    // Tache 6.8 — cache par defaut (total == 0 && !hasQueuedChunks).
    val importProgress: ImportProgress = ImportProgress(),
    val errorMessage: String? = null,
    val progressMap: Map<String, Int> = emptyMap(),
    // Lot 5 — résultats d'import consultables après la fin du worker
    val importResults: List<ImportResultEntry> = emptyList(),
    val showImportDetails: Boolean = false,
    val importSessionId: String? = null,
    // Lot 19 — progression live de la reconstruction des couvertures
    // (menu 3-points « Reconstruire les couvertures »).
    val isRegeneratingCovers: Boolean = false,
    val coverRegeneration: CoverRegenerationProgress? = null,
) {
    /** Tags distincts de la bibliotheque COMPLETE, pas seulement du filtre actif — le drawer doit pouvoir en changer. */
    val availableTags: List<String> get() = publications.flatMap { it.subjects }.distinct().sorted()
    val availableSeries: List<String> get() = publications.mapNotNull { it.seriesName }.distinct().sorted()
    val availableAuthors: List<String> get() = publications.flatMap { it.authors }.distinct().sorted()

    /** Lot 2a.3 — compteurs du flyout du titre (ex. « Trilogie du Vide (3) »). */
    val seriesCounts: Map<String, Int> get() = publications.mapNotNull { it.seriesName }.groupingBy { it }.eachCount()
    val tagCounts: Map<String, Int> get() = publications.flatMap { it.subjects }.groupingBy { it }.eachCount()

    /** Tache 9bis.4 — carte "reprendre la lecture" proeminente, pas seulement un FAB (amelioration legacy). */
    val resumeReadingPublication: Publication?
        get() = publications.filter { it.lastOpened != null }.maxByOrNull { it.lastOpened!! }

    val displayedPublications: List<Publication>
        get() = publications.filterAndSort(searchQuery, selectedFormats, sortOrder)
}

/**
 * Lot 2a.4 — factorisé pour être partagé avec `LibraryDetailUiState`
 * (écran de détail Séries/Tags), même filtre recherche/format/tri que
 * la Bibliothèque, pas de logique dupliquée.
 */
internal fun List<Publication>.filterAndSort(
    searchQuery: String,
    selectedFormats: Set<PublicationFormat>,
    sortOrder: LibrarySortOrder,
): List<Publication> {
    val searched = if (searchQuery.isBlank()) {
        this
    } else {
        filter { publication ->
            publication.title.contains(searchQuery, ignoreCase = true) ||
                publication.authors.any { it.contains(searchQuery, ignoreCase = true) }
        }
    }
    val filtered = if (selectedFormats.isEmpty()) {
        searched
    } else {
        searched.filter { it.format in selectedFormats }
    }
    val sorted = when (sortOrder) {
        LibrarySortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
        LibrarySortOrder.AUTHOR -> filtered.sortedWith(compareBy(nullsLast()) { it.authors.firstOrNull()?.lowercase() })
        LibrarySortOrder.RECENTLY_ADDED -> filtered.sortedByDescending { it.importDate }
        LibrarySortOrder.RECENTLY_OPENED -> filtered.sortedByDescending { it.lastOpened ?: 0L }
    }
    // Lot 2b.1 — les livres épinglés remontent en tête, quel que soit le
    // tri actif. sortedByDescending est stable : l'ordre issu du tri
    // ci-dessus est conservé au sein de chaque groupe (épinglé/non).
    return sorted.sortedByDescending { it.isPinned }
}

/** Lot 2a.1 — « Récents » et « Récemment lus » fusionnés en RECENTLY_OPENED (decision actee, un seul `lastOpened` dans le domaine). */
enum class LibrarySortOrder { RECENTLY_ADDED, TITLE, AUTHOR, RECENTLY_OPENED }

/** Lot 2a.1 — 2 dispositions, pas 3 : GRID (couverture + titre) retiree, decision finale UX (grille couvertures seules). */
enum class LibraryLayoutMode { LIST, GRID_COVERS }

/** Lot 19 — progression X/Y de la reconstruction des couvertures (menu legacy « progression live »). */
data class CoverRegenerationProgress(val processed: Int, val total: Int)

sealed interface LibraryIntent {
    data class OpenPublication(val publicationId: String) : LibraryIntent
    data class ToggleFavorite(val publicationId: String, val isFavorite: Boolean) : LibraryIntent
    data class TogglePin(val publicationId: String, val isPinned: Boolean) : LibraryIntent
    data class DeletePublication(val publicationId: String) : LibraryIntent
    data class ChangeFilter(val filter: FilterMode, val value: String? = null) : LibraryIntent
    data class SetSearchQuery(val query: String) : LibraryIntent
    data class SetSortOrder(val order: LibrarySortOrder) : LibraryIntent
    data class SetLayoutMode(val mode: LibraryLayoutMode) : LibraryIntent
    data class ToggleFileFormat(val format: PublicationFormat) : LibraryIntent
    data object ClearFileFormats : LibraryIntent
    data object Refresh : LibraryIntent
    data object DismissError : LibraryIntent
    data object DismissImportResults : LibraryIntent
    data object OpenImportDetails : LibraryIntent
    // Lot 19 — actions du menu 3-points
    data object OpenRandomBook : LibraryIntent
    data object SyncNow : LibraryIntent
    data object RegenerateCovers : LibraryIntent
    data object ResetCovers : LibraryIntent
}

/**
 * Effets ponctuels (Blueprint §4.4 : canal dédié, jamais mélangé à
 * l'état) — consommé par `InkToneNavHost` (Tâche 9bis.2) via
 * `onNavigateToReader`, qui traduit `publicationId` en `ReaderRoute`.
 */
sealed interface LibraryEffect {
    data class NavigateToReader(val publicationId: String) : LibraryEffect
    data object NavigateToStats : LibraryEffect
    // Lot 19 — « Synchroniser avec le cloud » non configuré : bascule
    // vers l'écran de configuration, jamais un bouton désactivé.
    data object NavigateToSync : LibraryEffect
    data class CoversRegenerated(val result: CoverRegenerationResult) : LibraryEffect
    data object CoversReset : LibraryEffect
}
