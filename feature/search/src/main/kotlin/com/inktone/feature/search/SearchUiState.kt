package com.inktone.feature.search

import com.inktone.domain.service.SearchResult
import com.inktone.domain.valueobject.Locator

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
)

sealed interface SearchIntent {
    data class QueryChanged(val query: String) : SearchIntent
    data class NavigateToResult(val result: SearchResult) : SearchIntent
}

/** Effet ponctuel (Blueprint §4.4) — la navigation réelle est câblée par l'appelant (`MainActivity`). */
sealed interface SearchEffect {
    data class NavigateToReader(val publicationId: String, val locator: Locator) : SearchEffect
}
