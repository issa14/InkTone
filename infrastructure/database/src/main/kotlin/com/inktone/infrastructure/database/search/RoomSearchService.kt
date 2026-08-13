package com.inktone.infrastructure.database.search

import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.Sentence
import com.inktone.domain.service.SearchResult
import com.inktone.domain.service.SearchService
import com.inktone.domain.valueobject.Locator
import com.inktone.infrastructure.database.dao.SentenceFtsDao
import com.inktone.infrastructure.database.entity.SentenceFtsEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémente [SearchService] via FTS4 (Tâche 7.3.3).
 *
 * Échappement (Tâche 7.3.3, point d'attention explicite du plan) :
 * toute la requête utilisateur est enveloppée dans une phrase FTS4 entre
 * guillemets (`"..."`, avec les guillemets internes doublés) — à
 * l'intérieur d'une phrase, FTS4 traite `*`, `-`, `NEAR`, `AND`, `OR`
 * comme du texte littéral plutôt que comme des opérateurs. Choix délibéré
 * plutôt qu'un filtrage caractère par caractère : plus simple, et le
 * comportement recherché ici est « cette suite de mots », pas un langage
 * de requête exposé à l'utilisateur.
 */
@Singleton
class RoomSearchService @Inject constructor(
    private val sentenceFtsDao: SentenceFtsDao,
) : SearchService {

    override suspend fun search(query: String, publicationId: String?): List<SearchResult> {
        val sanitized = sanitizeFtsQuery(query)
        if (sanitized.isBlank()) return emptyList()

        val rows = if (publicationId != null) {
            sentenceFtsDao.searchInPublication(sanitized, publicationId)
        } else {
            sentenceFtsDao.searchAll(sanitized)
        }
        return rows.map { it.toSearchResult() }
    }

    override suspend fun indexPublication(publicationId: String, documentModel: DocumentModel) {
        val entities = documentModel.chapters.flatMap { chapter ->
            chapter.sentences.map { sentence ->
                SentenceFtsEntity(
                    publicationId = publicationId,
                    chapterIndex = chapter.index,
                    resourceHref = chapter.href,
                    charOffset = sentence.startOffset,
                    text = sentence.text,
                )
            }
        }
        if (entities.isNotEmpty()) sentenceFtsDao.insertAll(entities)
    }

    override suspend fun indexSentences(
        publicationId: String,
        chapterIndex: Int,
        resourceHref: String,
        sentences: List<Sentence>,
    ) {
        val entities = sentences.map { sentence ->
            SentenceFtsEntity(
                publicationId = publicationId,
                chapterIndex = chapterIndex,
                resourceHref = resourceHref,
                charOffset = sentence.startOffset,
                text = sentence.text,
            )
        }
        if (entities.isNotEmpty()) sentenceFtsDao.insertAll(entities)
    }

    private fun sanitizeFtsQuery(query: String): String {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return ""
        return "\"${trimmed.replace("\"", "\"\"")}\""
    }

    private fun SentenceFtsEntity.toSearchResult() = SearchResult(
        publicationId = publicationId,
        locator = Locator(resourceHref = resourceHref, chapterIndex = chapterIndex, charOffset = charOffset),
        snippet = text,
    )
}
