package com.inktone.feature.reader.rendering

import coil.fetch.Fetcher
import com.inktone.domain.service.EpubResourceResolver

/**
 * [Fetcher] Coil pour les images EPUB via [EpubImageKey].
 *
 * TODO(device): finaliser l'intégration Coil 2.7. L'API `ImageSource`
 * est instable en 2.7 (constructeurs internes, `ImageSources` en
 * `@ExperimentalCoilApi`). Sera testé et finalisé sur device avec un
 * vrai EPUB avant le merge du Palier 3.
 */
class EpubResourceFetcher(
    private val resolver: EpubResourceResolver,
    private val key: EpubImageKey,
) : Fetcher {

    override suspend fun fetch(): coil.fetch.FetchResult? = null

    class Factory(
        private val resolver: EpubResourceResolver,
    ) : Fetcher.Factory<EpubImageKey> {
        override fun create(
            data: EpubImageKey,
            options: coil.request.Options,
            imageLoader: coil.ImageLoader,
        ): Fetcher? = EpubResourceFetcher(resolver, data)
    }
}
