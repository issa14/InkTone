package com.inktone.core.testing.fake

import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterLengths
import com.inktone.domain.service.PreAnalysisStore

/**
 * Fake pour [PreAnalysisStore] — stocke les pré-analyses en mémoire
 * (map par publicationId) et expose l'état pour les assertions
 * (`saved`, `deleted`).
 */
class FakePreAnalysisStore : PreAnalysisStore {

    private val storage = mutableMapOf<String, List<Chapter>>()
    private val hashes = mutableMapOf<String, String>()
    val deleted = mutableListOf<String>()

    override suspend fun save(publicationId: String, fileHash: String, chapters: List<Chapter>) {
        storage[publicationId] = chapters
        hashes[publicationId] = fileHash
    }

    override suspend fun loadChapter(publicationId: String, fileHash: String, chapterHref: String): Chapter? =
        storage[publicationId]?.takeIf { hashes[publicationId] == fileHash }?.firstOrNull { it.href == chapterHref }

    override suspend fun isFresh(publicationId: String, fileHash: String): Boolean =
        storage.containsKey(publicationId) && hashes[publicationId] == fileHash

    override suspend fun chapterLengths(publicationId: String, fileHash: String): ChapterLengths? =
        storage[publicationId]?.takeIf { hashes[publicationId] == fileHash }?.let(ChapterLengths::of)

    override suspend fun delete(publicationId: String) {
        storage.remove(publicationId)
        hashes.remove(publicationId)
        deleted += publicationId
    }

    fun savedChapters(publicationId: String): List<Chapter>? = storage[publicationId]
}
