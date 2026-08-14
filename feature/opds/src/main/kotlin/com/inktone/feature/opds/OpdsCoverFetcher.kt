package com.inktone.feature.opds

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.inktone.domain.service.OpdsDownloadResult
import com.inktone.domain.service.OpdsHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [Fetcher] Coil 2.7 pour les couvertures OPDS (Lot 13, tâche 13.2.5) —
 * même patron que `EpubImageFetcher` (feature/reader), mais réseau :
 * télécharge via [OpdsHttpClient] (interface domaine, jamais OkHttp ici)
 * qui résout l'auth Basic Auth par `catalogId` — pas de requête de
 * couverture sans en-tête sur un catalogue protégé (décision actée §13).
 */
class OpdsCoverFetcher(
    private val httpClient: OpdsHttpClient,
    private val key: OpdsCoverKey,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bytes = when (val result = httpClient.download(key.url, key.catalogId)) {
            is OpdsDownloadResult.Success -> result.bytes
            is OpdsDownloadResult.Failure -> throw IllegalStateException("Couverture indisponible : ${result.message}")
        }
        val bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } ?: throw IllegalStateException("Décodage de la couverture échoué")

        return DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = false,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(
        private val httpClient: OpdsHttpClient,
    ) : Fetcher.Factory<OpdsCoverKey> {
        override fun create(
            data: OpdsCoverKey,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = OpdsCoverFetcher(httpClient, data, options.context)
    }
}
