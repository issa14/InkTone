package com.inktone.domain.service

import com.inktone.domain.valueobject.Locator

/** Contrat implémenté par la recherche FTS (Blueprint §6.9, Phase 7). */
interface SearchService {
    suspend fun search(query: String, publicationId: String? = null): List<SearchResult>
}

data class SearchResult(
    val publicationId: String,
    val locator: Locator,
    val snippet: String,
)
