package com.inktone.domain.model

/**
 * Entrée d'un flux OPDS (Lot 13, ADR-023). Deux natures exclusives :
 * [Navigation] (dossier/sous-section, re-navigation dans le flux) et
 * [Book] (livre acquérable). Un lien `rel="subsection"`/`collection`
 * produit un [Navigation] ; un `rel` d'acquisition
 * (`http://opds-spec.org/acquisition/...`) produit un [Book].
 */
sealed interface OpdsItem {
    data class Navigation(
        val title: String,
        val href: String,
    ) : OpdsItem

    data class Book(
        val title: String,
        val authors: List<String>,
        val coverUrl: String?,
        val acquisitionHref: String,
        val mimeType: String,
    ) : OpdsItem
}
