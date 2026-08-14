package com.inktone.domain.usecase

import java.net.URLEncoder

/**
 * Recherche OpenSearch dans un flux OPDS (Lot 13, tâche 13.2.7).
 * Substitue `{searchTerms}` avec la requête utilisateur encodée
 * (`URLEncoder`, jamais une concaténation brute) puis délègue à
 * [BrowseOpdsFeedUseCase].
 */
class SearchOpdsFeedUseCase(
    private val browse: BrowseOpdsFeedUseCase,
) {
    suspend operator fun invoke(query: String, template: String, catalogId: String?): OpdsBrowseResult {
        require(query.isNotBlank()) { "query ne peut pas être vide" }
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = template.replace("{searchTerms}", encoded)
        return browse(url, catalogId)
    }
}
