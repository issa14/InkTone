package com.inktone.domain.usecase

import com.inktone.domain.model.OpdsFeed
import com.inktone.domain.repository.OpdsCatalogRepository
import com.inktone.domain.service.OpdsFailureReason
import com.inktone.domain.service.OpdsFeedParser
import com.inktone.domain.service.OpdsFetchResult
import com.inktone.domain.service.OpdsHttpClient
import com.inktone.domain.service.OpdsParseResult

/** Résultat de la navigation dans un flux OPDS (Lot 13, tâche 13.2.4). */
sealed interface OpdsBrowseResult {
    data class Success(val feed: OpdsFeed) : OpdsBrowseResult
    data class Failure(val reason: OpdsFailureReason, val message: String) : OpdsBrowseResult

    /** Garde-fou anti-boucle de pagination : l'URL demandée a déjà été visitée dans la session en cours. */
    data object LoopDetected : OpdsBrowseResult
}

/**
 * Récupère et parse un flux OPDS (Lot 13, ADR-023). Retient les URLs
 * déjà visitées pour la session de navigation en cours (décision actée
 * §14) : un `nextPageUrl` qui reboucle sur une URL déjà vue interrompt
 * la pagination ([OpdsBrowseResult.LoopDetected]) plutôt que de boucler
 * indéfiniment. `resetSession()` ouvre une nouvelle session (ouverture
 * d'un catalogue).
 */
class BrowseOpdsFeedUseCase(
    private val httpClient: OpdsHttpClient,
    private val parser: OpdsFeedParser,
    private val catalogRepository: OpdsCatalogRepository,
) {
    private val visitedUrls = mutableSetOf<String>()

    fun resetSession() = visitedUrls.clear()

    /** Navigation manuelle (dossier) — n'applique pas le garde-fou anti-boucle. */
    suspend operator fun invoke(url: String, catalogId: String?): OpdsBrowseResult {
        visitedUrls.add(url)
        return browse(url, catalogId)
    }

    /** Pagination — un `nextPageUrl` déjà visité interrompt la pagination. */
    suspend fun loadNextPage(url: String, catalogId: String?): OpdsBrowseResult {
        if (!visitedUrls.add(url)) return OpdsBrowseResult.LoopDetected
        return browse(url, catalogId)
    }

    private suspend fun browse(url: String, catalogId: String?): OpdsBrowseResult {
        val fetch = httpClient.fetch(url, catalogId)
        if (fetch is OpdsFetchResult.Failure) {
            return OpdsBrowseResult.Failure(fetch.reason, fetch.message)
        }
        val success = fetch as OpdsFetchResult.Success

        val parsed = parser.parse(success.body, success.finalUrl)
        if (parsed is OpdsParseResult.Failure) {
            return OpdsBrowseResult.Failure(parsed.reason, parsed.message)
        }
        val feed = (parsed as OpdsParseResult.Success).feed

        // Persiste le template OpenSearch annoncé par le flux racine
        // d'un catalogue (jamais pour un sous-flux).
        if (catalogId != null && feed.searchTemplateUrl != null) {
            val catalog = catalogRepository.getById(catalogId)
            if (catalog != null && catalog.rootUrl == url) {
                catalogRepository.updateSearchTemplate(catalogId, feed.searchTemplateUrl)
            }
        }

        return OpdsBrowseResult.Success(feed)
    }
}
