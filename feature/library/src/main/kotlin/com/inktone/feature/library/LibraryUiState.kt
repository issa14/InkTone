package com.inktone.feature.library

import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.Publication

/**
 * État unique et immuable de l'écran bibliothèque (MVI, Blueprint §4.4).
 * `activeFilter`/`filterValue` pilotent `PublicationRepository.observeFiltered`
 * (Tâche 6.5) — pas de sélecteur pour `SERIES`/`TAG`/`BY_AUTHOR` dans
 * cette passe (exigerait de lister les valeurs distinctes en base, hors
 * périmètre de « bibliothèque basique ») : seuls les modes sans valeur
 * (`ALL`, `FAVORITES`, `UNREAD`, `IN_PROGRESS`, `READ`) sont exposés à
 * l'utilisateur pour l'instant.
 */
data class LibraryUiState(
    val publications: List<Publication> = emptyList(),
    val isLoading: Boolean = true,
    val activeFilter: FilterMode = FilterMode.ALL,
)

sealed interface LibraryIntent {
    data class OpenPublication(val publicationId: String) : LibraryIntent
    data class ToggleFavorite(val publicationId: String, val isFavorite: Boolean) : LibraryIntent
    data class ChangeFilter(val filter: FilterMode) : LibraryIntent
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
