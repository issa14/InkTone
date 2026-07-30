package com.inktone.feature.library

import com.inktone.domain.model.Bookmark

/**
 * Tache 9bis.6 — signets tous livres confondus (`BookmarkRepository.observeAll`,
 * deja construit pour `BackupManager`, Tache 8.5 — pas une nouvelle
 * requete). `publicationTitle` resolu ici (pas dans `Bookmark`, qui ne
 * connait que `publicationId`) pour l'affichage.
 */
data class GlobalBookmarksUiState(
    val bookmarks: List<BookmarkWithPublicationTitle> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
) {
    val displayedBookmarks: List<BookmarkWithPublicationTitle>
        get() = if (searchQuery.isBlank()) {
            bookmarks
        } else {
            bookmarks.filter { it.publicationTitle.contains(searchQuery, ignoreCase = true) }
        }
}

data class BookmarkWithPublicationTitle(val bookmark: Bookmark, val publicationTitle: String)

sealed interface GlobalBookmarksIntent {
    data class SetSearchQuery(val query: String) : GlobalBookmarksIntent
    data class OpenBookmark(val bookmark: Bookmark) : GlobalBookmarksIntent
    data class DeleteBookmark(val id: String) : GlobalBookmarksIntent
}

sealed interface GlobalBookmarksEffect {
    data class NavigateToReader(
        val publicationId: String,
        val resourceHref: String,
        val chapterIndex: Int,
        val charOffset: Int,
    ) : GlobalBookmarksEffect
}
