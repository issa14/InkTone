package com.inktone.core.testing.fake

import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.Sentence
import com.inktone.domain.service.SearchResult
import com.inktone.domain.service.SearchService

class FakeSearchService : SearchService {
    val indexedDocuments = mutableMapOf<String, DocumentModel>()

    /** Chapitres indexés phrase par phrase : (index de chapitre, href), dans l'ordre d'appel. */
    val indexedChapters = mutableListOf<Pair<Int, String>>()
    var nextSearchResult: List<SearchResult> = emptyList()

    /** Livre demandé à chaque recherche (`null` = toute la bibliothèque), dans l'ordre. */
    val searchedPublicationIds = mutableListOf<String?>()

    override suspend fun search(query: String, publicationId: String?): List<SearchResult> {
        searchedPublicationIds += publicationId
        return nextSearchResult
    }

    override suspend fun indexPublication(publicationId: String, documentModel: DocumentModel) {
        indexedDocuments[publicationId] = documentModel
    }

    override suspend fun indexSentences(
        publicationId: String,
        chapterIndex: Int,
        resourceHref: String,
        sentences: List<Sentence>,
    ) {
        indexedChapters += chapterIndex to resourceHref
    }
}
