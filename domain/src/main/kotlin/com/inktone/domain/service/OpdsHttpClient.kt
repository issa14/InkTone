package com.inktone.domain.service

/**
 * Résultat d'une requête HTTP OPDS (Lot 13, tâche 13.2.3). [Success]
 * porte le corps brut et l'URL finale (après d'éventuelles redirections),
 * utile à la résolution des hrefs relatifs.
 */
sealed interface OpdsFetchResult {
    data class Success(val body: String, val finalUrl: String) : OpdsFetchResult
    data class Failure(val reason: OpdsFailureReason, val message: String) : OpdsFetchResult
}

/** Résultat d'un téléchargement d'octets OPDS (couverture, EPUB). */
sealed interface OpdsDownloadResult {
    data class Success(val bytes: ByteArray) : OpdsDownloadResult
    data class Failure(val reason: OpdsFailureReason, val message: String) : OpdsDownloadResult
}

/**
 * Client HTTP OPDS (Lot 13, ADR-023) — abstraction du domaine sur
 * OkHttp, pour que `feature:opds` ne dépende jamais du réseau
 * (Blueprint §12.4). L'implémentation (`infrastructure:opds`) résout les
 * identifiants Basic Auth par `catalogId` et pose l'en-tête
 * `Authorization` requête par requête — jamais un intercepteur global
 * (écart délibéré §2 du plan).
 */
interface OpdsHttpClient {
    suspend fun fetch(url: String, catalogId: String?): OpdsFetchResult

    /** Télécharge des octets (couverture, EPUB) — même résolution d'auth par `catalogId`. */
    suspend fun download(url: String, catalogId: String?): OpdsDownloadResult
}
