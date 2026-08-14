package com.inktone.domain.model

/**
 * Catalogue OPDS (Lot 13, Volet 1, ADR-023) — une source de bibliothèque
 * distante, jamais une position de lecture (la navigation OPDS ne touche
 * jamais [com.inktone.domain.valueobject.Locator]).
 *
 * [hasCredentials] est porté par le modèle pour que l'UI sache afficher
 * un badge « protégé » sans jamais manipuler les identifiants eux-mêmes :
 * ils vivent chiffrés dans `OpdsCredentialsStore`, pas ici.
 */
data class OpdsCatalog(
    val id: String,
    val name: String,
    val rootUrl: String,
    val searchTemplateUrl: String?,
    val hasCredentials: Boolean,
) {
    init {
        require(id.isNotBlank()) { "id ne peut pas être vide" }
        require(name.isNotBlank()) { "name ne peut pas être vide" }
        require(rootUrl.isNotBlank()) { "rootUrl ne peut pas être vide" }
    }
}
