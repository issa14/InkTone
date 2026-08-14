package com.inktone.domain.model

/**
 * Flux OPDS 1.2/Atom parsé (Lot 13, ADR-023) — un écran de navigation,
 * pas un document : [items] (dossiers et livres mélangés, dans l'ordre
 * du flux), [nextPageUrl] (pagination) et [searchTemplateUrl] (OpenSearch,
 * nullable — la loupe n'apparaît que s'il est annoncé).
 */
data class OpdsFeed(
    val title: String,
    val items: List<OpdsItem>,
    val nextPageUrl: String?,
    val searchTemplateUrl: String?,
)
