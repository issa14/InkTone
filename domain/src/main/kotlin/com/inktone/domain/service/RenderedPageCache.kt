package com.inktone.domain.service

import com.inktone.domain.model.RenderedPage

/**
 * Cache disque des pages PDF rendues (Lot 22, Palier C, tâche 9) — évite
 * une nouvelle rasterisation PDFium à chaque réouverture d'un document déjà
 * lu. Clé = `(publicationId, pageIndex, targetWidthPx)` : une résolution
 * différente (rotation, redimensionnement de fenêtre) n'est jamais servie
 * pour une autre. Purgé avec la publication ([deletePublication]), comme
 * [TtsSegmentCache] et [PreAnalysisStore] — ces caches vivent hors Room et
 * ne bénéficient donc pas du `ON DELETE CASCADE`.
 */
interface RenderedPageCache {

    /** Retourne la page cachée pour cette clé, ou `null` (absente). */
    suspend fun get(publicationId: String, pageIndex: Int, targetWidthPx: Int): RenderedPage?

    /** Stocke [page] sous cette clé. */
    suspend fun put(publicationId: String, pageIndex: Int, targetWidthPx: Int, page: RenderedPage)

    /** Purge le cache de pages d'une publication (suppression du livre). */
    suspend fun deletePublication(publicationId: String)
}
