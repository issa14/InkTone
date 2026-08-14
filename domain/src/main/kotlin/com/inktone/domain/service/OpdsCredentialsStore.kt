package com.inktone.domain.service

/**
 * Identifiants Basic Auth d'un catalogue OPDS (Lot 13, ADR-023). Jamais
 * persistés en clair : l'implémentation (`infrastructure:opds`) les
 * chiffre via `EncryptedSharedPreferences` keyée par `catalogId`, même
 * famille que `SecureAuthStateStore` (Lot 11).
 */
data class OpdsCredentials(
    val username: String,
    val password: String,
)

/**
 * Contrat de stockage des identifiants OPDS, keyé par `catalogId` —
 * un catalogue n'est pas un compte de sync, fichier distinct de
 * `SecureAuthStateStore` par décision actée (Lot 13, §5).
 */
interface OpdsCredentialsStore {
    fun hasCredentials(catalogId: String): Boolean
    fun getCredentials(catalogId: String): OpdsCredentials?
    fun setCredentials(catalogId: String, username: String, password: String)
    fun clearCredentials(catalogId: String)
}
