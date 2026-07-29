package com.inktone.core.testing.fake

import com.inktone.domain.model.DocumentModel
import com.inktone.domain.service.SearchResult
import com.inktone.domain.service.SearchService

class FakeSearchService : SearchService {
    val indexedDocuments = mutableMapOf<String, DocumentModel>()
    var nextSearchResult: List<SearchResult> = emptyList()

    override suspend fun search(query: String, publicationId: String?): List<SearchResult> = nextSearchResult

    override suspend fun indexPublication(publicationId: String, documentModel: DocumentModel) {
        indexedDocuments[publicationId] = documentModel
    }
}
