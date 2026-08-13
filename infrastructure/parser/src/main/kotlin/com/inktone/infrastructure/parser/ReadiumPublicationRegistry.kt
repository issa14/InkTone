package com.inktone.infrastructure.parser

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.DefaultContentService
import org.readium.r2.shared.publication.services.content.contentServiceFactory
import org.readium.r2.shared.publication.services.content.iterators.HtmlResourceContentIterator
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point d'ouverture UNIQUE des [Publication] Readium par `publicationId` (K2,
 * CLAUDE.md — "une seule ouverture ZIP par import EPUB").
 *
 * Avant cette classe, [EpubChapterParser] (texte) et [ReadiumResourceResolver]
 * (images) ouvraient chacun leur propre [Publication] pour le même livre —
 * deux lectures complètes du central directory ZIP par session de lecture.
 * Les deux délèguent maintenant ici : la première à appeler [getOrOpen]
 * pour un `publicationId` donné paie le coût d'ouverture, l'autre réutilise
 * l'instance en cache.
 */
@OptIn(ExperimentalReadiumApi::class)
@Singleton
class ReadiumPublicationRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
) {
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
            onCreatePublication = {
                servicesBuilder.contentServiceFactory = DefaultContentService.createFactory(
                    resourceContentIteratorFactories = listOf(HtmlResourceContentIterator.Factory()),
                )
            },
        )
    }

    private val publicationFiles = ConcurrentHashMap<String, String>()
    private val openPublications = ConcurrentHashMap<String, Publication>()
    private val openMutex = Mutex()

    /** Enregistre le mapping `publicationId → fileUri`. Idempotent. */
    fun register(publicationId: String, fileUri: String) {
        publicationFiles[publicationId] = fileUri
    }

    /**
     * Retourne la [Publication] ouverte pour `publicationId`, en l'ouvrant
     * paresseusement si nécessaire. [register] doit avoir été appelé avant.
     */
    suspend fun getOrOpen(publicationId: String): Publication {
        openPublications[publicationId]?.let { return it }

        val fileUri = publicationFiles[publicationId]
            ?: throw IllegalStateException(
                "Publication $publicationId non enregistrée — appeler register() d'abord",
            )

        // Mutex évite la race condition : deux appels concurrents pour le
        // même publicationId (ex. EpubChapterParser et ReadiumResourceResolver
        // ouvrant en parallèle) ne peuvent pas ouvrir deux Publications.
        return openMutex.withLock {
            openPublications[publicationId]?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                openPublication(fileUri).also { pub -> openPublications[publicationId] = pub }
            }
        }
    }

    /** Ferme la [Publication] et oublie le mapping pour `publicationId`. Idempotent. */
    fun release(publicationId: String) {
        openPublications.remove(publicationId)?.close()
        publicationFiles.remove(publicationId)
    }

    /**
     * Repli quand `Publication.linkWithHref` ne trouve pas [entryHref] —
     * bug réel Android : l'accès aux entrées ZIP est sensible à la casse
     * (contrairement à Windows/macOS, où l'EPUB a pu être généré/édité).
     * Lecture ZIP directe via [EpubZipAccess], hors du manifeste Readium.
     */
    suspend fun readAssetIgnoreCase(publicationId: String, entryHref: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val fileUri = publicationFiles[publicationId] ?: return@withContext null
            EpubZipAccess.readEntryBytes(context, fileUri, entryHref, ignoreCase = true)
        }

    private suspend fun openPublication(fileUri: String): Publication {
        val url = if (fileUri.contains("://")) {
            Uri.parse(fileUri).toAbsoluteUrl()
                ?: throw IllegalStateException("URI non absolue: $fileUri")
        } else {
            File(fileUri).toUrl()
        }

        val asset = assetRetriever.retrieve(url).getOrElse {
            throw IllegalStateException("Echec de lecture de l'asset EPUB: $it")
        }

        return publicationOpener.open(asset, allowUserInteraction = false).getOrElse {
            throw IllegalStateException("Echec d'ouverture de la publication EPUB: $it")
        }
    }
}
