package com.inktone.core.testing.fake

import com.inktone.domain.model.Chapter
import com.inktone.domain.service.PreAnalysisStore

/**
 * Fake pour [PreAnalysisStore] — stocke les pré-analyses en mémoire
 * (map par publicationId) et expose l'état pour les assertions
 * (`saved`, `deleted`).
 */
class FakePreAnalysisStore : PreAnalysisStore {

    private val storage = mutableMapOf<String, List<Chapter>>()
    val deleted = mutableListOf<String>()

    override suspend fun save(publicationId: String, fileHash: String, chapters: List<Chapter>) {
        storage[publicationId] = chapters
    }

    override suspend fun load(publicationId: String, fileHash: String): List<Chapter>? =
        storage[publicationId]

    override suspend fun delete(publicationId: String) {
        storage.remove(publicationId)
        deleted += publicationId
    }

    fun savedChapters(publicationId: String): List<Chapter>? = storage[publicationId]
}
