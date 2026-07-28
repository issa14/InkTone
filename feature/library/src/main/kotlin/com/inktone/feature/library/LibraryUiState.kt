package com.inktone.feature.library

import com.inktone.domain.model.Publication

/**
 * État unique et immuable de l'écran bibliothèque (MVI, Blueprint §4.4).
 * Pas de filtre actif ici (Tâche 6.6 délibérément avant 6.5, cf.
 * PHASE_6_LIBRARY_IMPORT.md) — `LibraryViewModel` observe `observeAll()`
 * telle quelle ; `activeFilter` sera ajouté quand
 * `PublicationRepository.observeFiltered` (Tâche 6.5) existera.
 */
data class LibraryUiState(
    val publications: List<Publication> = emptyList(),
    val isLoading: Boolean = true,
)

sealed interface LibraryIntent {
    data class OpenPublication(val publicationId: String) : LibraryIntent
    data class ToggleFavorite(val publicationId: String, val isFavorite: Boolean) : LibraryIntent
}

/**
 * Effets ponctuels (Blueprint §4.4 : canal dédié, jamais mélangé à
 * l'état) — la navigation réelle vers `feature/reader` n'existe pas
 * encore (aucun graphe de navigation dans l'app, `MainActivity` héberge
 * `ReaderScreen` directement) : ce canal est prêt à être consommé dès
 * que ce câblage existera, pas une navigation fabriquée par anticipation.
 */
sealed interface LibraryEffect {
    data class NavigateToReader(val publicationId: String) : LibraryEffect
}
