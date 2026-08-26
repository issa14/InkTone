package com.inktone.core.testing.fake

import com.inktone.domain.model.RenderedPage
import com.inktone.domain.service.RenderedPageCache

/**
 * Fake pour [RenderedPageCache] — stocke les pages en mémoire et expose
 * `deletedPublications` pour les assertions de purge.
 */
class FakeRenderedPageCache : RenderedPageCache {

    val stored = mutableMapOf<Triple<String, Int, Int>, RenderedPage>()
    val deletedPublications = mutableListOf<String>()

    override suspend fun get(publicationId: String, pageIndex: Int, targetWidthPx: Int): RenderedPage? =
        stored[Triple(publicationId, pageIndex, targetWidthPx)]

    override suspend fun put(publicationId: String, pageIndex: Int, targetWidthPx: Int, page: RenderedPage) {
        stored[Triple(publicationId, pageIndex, targetWidthPx)] = page
    }

    override suspend fun deletePublication(publicationId: String) {
        deletedPublications += publicationId
        stored.keys.removeAll { it.first == publicationId }
    }
}
