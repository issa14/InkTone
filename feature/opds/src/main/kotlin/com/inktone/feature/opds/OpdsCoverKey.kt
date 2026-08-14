package com.inktone.feature.opds

/**
 * Clé de couverture OPDS pour Coil (Lot 13, tâche 13.2.5). [catalogId]
 * porte le catalogue propriétaire de la couverture : le [OpdsCoverFetcher]
 * résout ainsi les identifiants Basic Auth par catalogue, jamais un
 * intercepteur global (décision actée §13).
 */
data class OpdsCoverKey(
    val url: String,
    val catalogId: String?,
)
