package com.inktone.feature.reader.rendering

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.inktone.domain.service.EpubResourceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [Fetcher] Coil 2.7 pour les images EPUB via [EpubImageKey].
 *
 * Contourne `ImageSource`/`ImageSources` (API instable en 2.7 :
 * `@ExperimentalCoilApi`, constructeurs internes) en décodant le bitmap
 * directement via `BitmapFactory` puis en l'enveloppant dans un
 * [DrawableResult] — seule API publique stable restante en 2.7.
 *
 * ## Pourquoi pas Coil 3.x ?
 *
 * Coil 3.x exige Kotlin 2.2.0+ (metadata binaire). Le projet est en
 * Kotlin 2.0.20. L'upgrade Kotlin → 2.2.0 est un chantier transverse
 * hors scope de cette refonte.
 */
class EpubImageFetcher(
    private val resolver: EpubResourceResolver,
    private val key: EpubImageKey,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val stream = resolver.openStream(key.publicationId, key.resourceHref)
            ?: throw IllegalStateException(
                "Image EPUB non trouvée : ${key.resourceHref}",
            )

        val bytes = stream.use { it.readBytes() }
        val bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } ?: throw IllegalStateException(
            "Décodage image échoué : ${key.resourceHref}",
        )

        return DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(
        private val resolver: EpubResourceResolver,
    ) : Fetcher.Factory<EpubImageKey> {
        override fun create(
            data: EpubImageKey,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = EpubImageFetcher(resolver, data, options.context)
    }
}
