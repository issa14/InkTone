package com.inktone.domain.usecase

import com.inktone.domain.service.SearchResult
import com.inktone.domain.service.SearchService

/**
 * Recherche plein texte dans la bibliothèque, ou dans une publication
 * donnée si `publicationId` est fourni.
 */
class SearchPublicationUseCase(
    private val searchService: SearchService,
) {
    suspend operator fun invoke(query: String, publicationId: String? = null): List<SearchResult> {
        if (query.isBlank()) return emptyList() // pas de requete vide vers FTS
        return searchService.search(query.trim(), publicationId)
    }
}
