package com.inktone.feature.library

import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.usecase.resumePublication
import com.inktone.domain.service.ImportProgress
import com.inktone.domain.service.ImportResultEntry
import com.inktone.domain.service.SyncOperationResult
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
    val layoutMode: LibraryLayoutMode = LibraryLayoutMode.GRID_DETAILED,
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
    /**
     * Publication actuellement narrée par la session TTS, et si elle joue —
     * alimentent le bouton Lecture/Pause de la carte « Reprendre la lecture ».
     * Dérivés de `PlaybackSession` (source de vérité unique, K3), jamais un
     * drapeau maintenu à la main ici : une pause venue de la notification ou
     * du Lecteur doit s'y refléter sans une ligne de synchronisation.
     */
    val narratingPublicationId: String? = null,
    val isNarrationPlaying: Boolean = false,
    // AUDIT_REACTIVITE_UX §3.5 — ces sept dérivations étaient des `get()`
    // rejouées à CHAQUE recomposition de LibraryScreen (dont à chaque frappe,
    // sans debounce). Elles sont désormais des champs calculés UNE FOIS par
    // émission dans LibraryViewModel (voir `withDerivedFields`), à l'image de
    // `computeProgressMap` déjà déplacé sur `defaultDispatcher`.
    val availableTags: List<String> = emptyList(),
    val availableSeries: List<String> = emptyList(),
    val availableAuthors: List<String> = emptyList(),
    val seriesCounts: Map<String, Int> = emptyMap(),
    val tagCounts: Map<String, Int> = emptyMap(),
    /**
     * Tache 9bis.4 — carte "reprendre la lecture" proeminente, pas seulement
     * un FAB (amelioration legacy). La regle vit dans `resumePublication()`
     * (domaine), partagee avec le mini-lecteur qui doit savoir s'il ferait
     * doublon avec cette carte — jamais deux definitions du meme "dernier
     * livre ouvert".
     */
    val resumeReadingPublication: Publication? = null,
    val displayedPublications: List<Publication> = emptyList(),
) {

    /** Vrai si la carte « Reprendre la lecture » porte le livre en cours de narration. */
    val isResumeNarrationPlaying: Boolean
        get() = isNarrationPlaying && narratingPublicationId != null &&
            narratingPublicationId == resumeReadingPublication?.id
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
        // §3.5 — clés de tri précalculées : `sortedBy { it.title.lowercase() }`
        // réévalue le sélecteur (donc alloue une String) à CHAQUE comparaison,
        // soit O(n log n) allocations. Ici la clé est calculée une fois par
        // élément (O(n)), puis le tri compare la clé déjà en cache.
        LibrarySortOrder.TITLE -> filtered
            .map { it to it.title.lowercase() }
            .sortedBy { it.second }
            .map { it.first }
        LibrarySortOrder.AUTHOR -> filtered
            .map { it to it.authors.firstOrNull()?.lowercase() }
            .sortedWith(compareBy(nullsLast()) { it.second })
            .map { it.first }
        LibrarySortOrder.RECENTLY_ADDED -> filtered.sortedByDescending { it.importDate }
        LibrarySortOrder.RECENTLY_OPENED -> filtered.sortedByDescending { it.lastOpened ?: 0L }
    }
    // Lot 2b.1 — les livres épinglés remontent en tête, quel que soit le
    // tri actif. sortedByDescending est stable : l'ordre issu du tri
    // ci-dessus est conservé au sein de chaque groupe (épinglé/non).
    return sorted.sortedByDescending { it.isPinned }
}

/**
 * AUDIT_REACTIVITE_UX §3.5 — calcule les sept dérivations de [LibraryUiState]
 * une fois, à partir des champs de base (publications + filtre/tri/recherche).
 * Appelée dans LibraryViewModel à chaque émission où ces champs changent (et
 * après le debounce de la recherche), jamais dans la composition.
 */
internal fun LibraryUiState.withDerivedFields(): LibraryUiState = copy(
    availableTags = publications.flatMap { it.subjects }.distinct().sorted(),
    availableSeries = publications.mapNotNull { it.seriesName }.distinct().sorted(),
    availableAuthors = publications.flatMap { it.authors }.distinct().sorted(),
    seriesCounts = publications.mapNotNull { it.seriesName }.groupingBy { it }.eachCount(),
    tagCounts = publications.flatMap { it.subjects }.groupingBy { it }.eachCount(),
    resumeReadingPublication = publications.resumePublication(),
    displayedPublications = publications.filterAndSort(searchQuery, selectedFormats, sortOrder),
)

/** Lot 2a.1 — « Récents » et « Récemment lus » fusionnés en RECENTLY_OPENED (decision actee, un seul `lastOpened` dans le domaine). */
enum class LibrarySortOrder { RECENTLY_ADDED, TITLE, AUTHOR, RECENTLY_OPENED }

/** Lot 2a.1 — 2 dispositions, pas 3 : GRID (couverture + titre) retiree, decision finale UX (grille couvertures seules). */
/**
 * Dispositions de la Bibliothèque. Persistée en TEXTE dans
 * `UserPreferences.libraryLayoutMode` et relue via
 * `runCatching { valueOf(...) }` — ajouter une entrée ici ne demande donc
 * aucune migration Room (la colonne n'est pas contrainte), et une valeur
 * inconnue retombe proprement sur le défaut.
 *
 * [GRID_DETAILED] est le défaut des NOUVELLES installations : un mur de
 * jaquettes seules ne dit ni le titre ni l'auteur, illisibles à 120 dp dès
 * que l'illustration est chargée ou générique. [GRID_COVERS] reste offert
 * tel quel — c'est un choix esthétique légitime, pas un défaut à corriger.
 */
enum class LibraryLayoutMode { LIST, GRID_COVERS, GRID_DETAILED }

/** Lot 19 — progression X/Y de la reconstruction des couvertures (menu legacy « progression live »). */
data class CoverRegenerationProgress(val processed: Int, val total: Int)

sealed interface LibraryIntent {
    data class OpenPublication(val publicationId: String, val autoStartTts: Boolean = false) : LibraryIntent

    /**
     * Bouton Lecture/Pause de la carte « Reprendre la lecture » — bascule la
     * narration SANS ouvrir le Lecteur (l'ouverture reste le tap sur la carte).
     */
    data class ToggleResumeNarration(val publicationId: String) : LibraryIntent
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
    data class NavigateToReader(val publicationId: String, val autoStartTts: Boolean = false) : LibraryEffect
    data object NavigateToStats : LibraryEffect
    // Lot 19 — « Synchroniser avec le cloud » non configuré : bascule
    // vers l'écran de configuration, jamais un bouton désactivé.
    data object NavigateToSync : LibraryEffect
    data class CoversRegenerated(val result: CoverRegenerationResult) : LibraryEffect
    data object CoversReset : LibraryEffect
    // Retours des actions du menu 3-points — mêmes règles que les
    // couvertures : un retour utilisateur, jamais un no-op muet.
    data object RandomBookUnavailable : LibraryEffect
    data class SyncCompleted(val result: SyncOperationResult) : LibraryEffect
}
