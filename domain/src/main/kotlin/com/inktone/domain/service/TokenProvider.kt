package com.inktone.domain.service

/**
 * Fournit un jeton d'accès valide pour le fournisseur de synchronisation
 * actif (tâche 11.4). Rafraîchissement transparent à l'intérieur de
 * l'implémentation : cette fonction rend toujours un jeton valide, ou
 * échoue explicitement (exception) — jamais un jeton périmé.
 */
fun interface TokenProvider {
    suspend fun getValidToken(): String
}
