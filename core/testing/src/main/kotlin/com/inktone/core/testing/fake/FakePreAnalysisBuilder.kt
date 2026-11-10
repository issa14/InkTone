package com.inktone.core.testing.fake

import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.service.PreAnalysisBuilder

/**
 * Fake pour [PreAnalysisBuilder] — produit un chapitre vide par href, sans
 * parsing. [failingFileUris] simule une source illisible ; les appels sont
 * exposés pour les assertions.
 */
class FakePreAnalysisBuilder(
    private val failingFileUris: Set<String> = emptySet(),
) : PreAnalysisBuilder {

    val analyzedFileUris = mutableListOf<String>()
    val rewrittenPublicationIds = mutableListOf<String>()

    override suspend fun analyzeAll(fileUri: String, chapterHrefs: List<String>): List<Chapter> {
        if (fileUri in failingFileUris) throw IllegalStateException("Source illisible : $fileUri")
        analyzedFileUris += fileUri
        return chapterHrefs.mapIndexed { index, href ->
            Chapter(index = index, href = href, title = null, content = ChapterContent.Rich(blocks = emptyList()))
        }
    }

    override fun onPreAnalysisRewritten(publicationId: String) {
        rewrittenPublicationIds += publicationId
    }
}
