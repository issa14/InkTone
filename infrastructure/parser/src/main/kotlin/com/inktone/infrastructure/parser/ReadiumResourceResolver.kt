package com.inktone.infrastructure.parser

import android.content.Context
import android.net.Uri
import com.inktone.domain.service.EpubResourceResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/**
 * Implémente [EpubResourceResolver] via Readium.
 *
 * Ouvre une [Publication] Readium à partir d'un `fileUri` et expose les
 * `InputStream` des ressources internes (images, polices, etc.).
 *
 * ## Cycle de vie
 *
 * - [open] : ouvre la [Publication] (appelé par le ViewModel après l'import).
 * - [openStream] : retourne un flux vers une ressource.
 * - [close] : ferme la [Publication] (appelé par `DisposableEffect` dans
 *   `ReaderScreen`).
 *
 * Cette classe est instanciée par Hilt avec un scope ViewModel —
 * chaque `ReaderViewModel` reçoit sa propre instance, et la
 * `Publication` est fermée quand le ViewModel est détruit.
 */
@OptIn(ExperimentalReadiumApi::class)
class ReadiumResourceResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : EpubResourceResolver {

    private var publication: Publication? = null
    private var publicationFileUri: String? = null

    private val httpClient by lazy { DefaultHttpClient() }
    private val assetRetriever by lazy {
        AssetRetriever(contentResolver = context.contentResolver, httpClient = httpClient)
    }
    private val publicationOpener by lazy {
        PublicationOpener(
            publicationParser = DefaultPublicationParser(
                context,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = null,
            ),
        )
    }

    /**
     * Ouvre l'EPUB à [fileUri] pour résoudre les ressources.
     *
     * Doit être appelé avant [openStream]. Idempotent : si la même
     * publication est déjà ouverte, ne fait rien.
     */
    suspend fun open(fileUri: String) {
        if (publication != null && publicationFileUri == fileUri) return

        // Fermer l'ancienne si différente
        close()

        publicationFileUri = fileUri
        publication = withContext(Dispatchers.IO) {
            val url = if (fileUri.contains("://")) {
                Uri.parse(fileUri).toAbsoluteUrl()
                    ?: throw IllegalStateException("URI non absolue: $fileUri")
            } else {
                File(fileUri).toUrl()
            }

            val asset = assetRetriever.retrieve(url).getOrElse {
                throw IllegalStateException("Echec de lecture de l'asset EPUB: $it")
            }

            publicationOpener.open(asset, allowUserInteraction = false).getOrElse {
                throw IllegalStateException("Echec d'ouverture de la publication EPUB: $it")
            }
        }
    }

    override suspend fun openStream(
        publicationId: String,
        resourceHref: String,
    ): InputStream? {
        val pub = publication ?: return null

        return withContext(Dispatchers.IO) {
            val link = pub.readingOrder.find { it.href.toString() == resourceHref }
                ?: pub.resources.find { it.href.toString() == resourceHref }
                ?: return@withContext null

            val resource: Resource = pub.get(link) ?: return@withContext null
            val length = resource.length().getOrElse { return@withContext null }.toLong()
            val bytes = resource.read(0L until length).getOrElse { return@withContext null }
            ByteArrayInputStream(bytes)
        }
    }

    override fun close() {
        publication?.close()
        publication = null
        publicationFileUri = null
    }
}
