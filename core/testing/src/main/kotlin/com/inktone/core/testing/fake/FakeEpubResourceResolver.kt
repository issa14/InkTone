package com.inktone.core.testing.fake

import com.inktone.domain.service.EpubResourceResolver
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Fake pour [EpubResourceResolver] — retourne toujours `null` pour
 * [openStream]. Les tests d'image EPUB passent par des `androidTest`
 * avec de vraies ressources.
 */
class FakeEpubResourceResolver : EpubResourceResolver {

    override suspend fun open(publicationId: String, fileUri: String) {
        // No-op
    }

    override suspend fun openStream(publicationId: String, resourceHref: String): InputStream? = null

    override fun close() {
        // No-op
    }
}
