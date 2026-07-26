package com.inktone.domain.usecase

import com.inktone.domain.service.SearchResult
import com.inktone.domain.service.SearchService

/**
 * Recherche plein texte dans la bibliothèque, ou dans une publication
 * donnée si `publicationId` est fourni.
 *
 * SIGNATURE UNIQUEMENT en Phase 1 — le corps réel exige [SearchService]
 * (recherche FTS, complété en Phase 7). Ne pas invoquer avant
 * l'injection d'une implémentation réelle.
 */
class SearchPublicationUseCase(
    private val searchService: SearchService,
) {
    suspend operator fun invoke(query: String, publicationId: String? = null): List<SearchResult> {
        TODO("Complété en Phase 7 — nécessite un SearchService réel (FTS)")
    }
}
