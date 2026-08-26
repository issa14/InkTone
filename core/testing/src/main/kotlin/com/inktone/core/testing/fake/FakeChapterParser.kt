package com.inktone.core.testing.fake

import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.service.ChapterParser

/**
 * Fake pour [ChapterParser] — retourne des chapitres vides, sans parsing.
 * Les tests qui ont besoin d'un contenu spécifique construisent
 * directement les [Chapter] dans leur fixture.
 */
class FakeChapterParser : ChapterParser {

    private val registered = mutableMapOf<String, String>()

    override fun registerPublication(publicationId: String, fileUri: String) {
        registered[publicationId] = fileUri
    }

    /** Correctif — vérifie que `registerPublication` a bien été appelé, tous formats confondus. */
    fun isRegistered(publicationId: String): Boolean = registered.containsKey(publicationId)

    override suspend fun parseChapter(
        publicationId: String,
        chapterHref: String,
        fragment: String?,
    ): Chapter = Chapter(
        index = 0,
        href = chapterHref,
        title = null,
        content = ChapterContent.Rich(blocks = emptyList()),
    )

    override fun preload(
        publicationId: String,
        chapterHref: String,
        scope: kotlinx.coroutines.CoroutineScope,
    ): kotlinx.coroutines.Job =
        kotlinx.coroutines.Job().apply { complete() }

    override fun invalidate(publicationId: String) {
        registered.remove(publicationId)
    }
}
