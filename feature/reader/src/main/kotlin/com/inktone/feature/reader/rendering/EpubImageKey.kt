package com.inktone.feature.reader.rendering

/**
 * Clé pour les images embarquées dans un EPUB.
 *
 * Identifie de manière unique une ressource image par son
 * `publicationId` et son `resourceHref` dans l'archive EPUB.
 * Utilisé comme donnée de requête Coil avec [EpubResourceFetcher].
 *
 * Pas d'URI scheme custom — Coil 2.x utilise la classe directement
 * comme type de données pour le [coil.fetch.Fetcher.Factory].
 */
data class EpubImageKey(
    val publicationId: String,
    val resourceHref: String,
) {
    val cacheKey: String get() = "$publicationId:$resourceHref"
}
