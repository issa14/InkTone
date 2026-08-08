package com.inktone.feature.library

import com.inktone.domain.model.Publication

/**
 * Lot 8 — écran Récents (UX §Récents). Réutilise [computeProgressMap]
 * (partagé avec Bibliothèque/Détail) pour dériver la progression, mais
 * n'a ni recherche, ni filtre de format, ni bascule de disposition :
 * vue Liste forcée, seuil ≥1% et limite 30 sont les seules règles.
 */
data class RecentsUiState(
    val publications: List<Publication> = emptyList(),
    val isLoading: Boolean = true,
    val progressMap: Map<String, Int> = emptyMap(),
) {
    /**
     * Seuls les livres commencés (≥1%, cf. [computeProgressMap] qui
     * plancher déjà toute progression entamée à 1), triés par
     * `lastOpened` décroissant, limités aux 30 plus récents. Les livres
     * terminés (100%) restent inclus — décision Tâche 8.2 : « récemment
     * consultés », pas « en cours de lecture » seul, pour ne pas faire
     * disparaître un livre de la liste au moment où il vient d'être fini.
     */
    val displayedPublications: List<Publication>
        get() = publications
            .filter { (progressMap[it.id] ?: 0) >= 1 }
            .sortedByDescending { it.lastOpened ?: 0L }
            .take(30)
}

sealed interface RecentsIntent {
    data class OpenPublication(val publicationId: String) : RecentsIntent
    data class ToggleFavorite(val publicationId: String, val isFavorite: Boolean) : RecentsIntent
    data class TogglePin(val publicationId: String, val isPinned: Boolean) : RecentsIntent
    data class DeletePublication(val publicationId: String) : RecentsIntent
}

sealed interface RecentsEffect {
    data class NavigateToReader(val publicationId: String) : RecentsEffect
}
