package com.inktone.domain.service

import com.inktone.domain.model.DocumentModel
import com.inktone.domain.valueobject.Locator

/** Contrat implémenté par la recherche FTS (Blueprint §6.9, Phase 7). */
interface SearchService {
    suspend fun search(query: String, publicationId: String? = null): List<SearchResult>

    /**
     * Peuple l'index à partir d'un [DocumentModel] déjà extrait (Tâche
     * 7.3.2 — à l'import, pas de second passage de parsing). Appelé par
     * [com.inktone.domain.usecase.ImportPublicationUseCase] après
     * insertion de la publication.
     */
    suspend fun indexPublication(publicationId: String, documentModel: DocumentModel)
}

data class SearchResult(
    val publicationId: String,
    val locator: Locator,
    val snippet: String,
)
