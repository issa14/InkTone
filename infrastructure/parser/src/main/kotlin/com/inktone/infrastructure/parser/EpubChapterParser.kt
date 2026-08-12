package com.inktone.infrastructure.parser

import android.content.Context
import android.net.Uri
import com.inktone.domain.model.Chapter
import com.inktone.domain.service.ChapterParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.DefaultContentService
import org.readium.r2.shared.publication.services.content.contentServiceFactory
import org.readium.r2.shared.publication.services.content.iterators.HtmlResourceContentIterator
import org.readium.r2.shared.util.Url
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémente [ChapterParser] pour les EPUB via Readium (accès aux
 * ressources ZIP) et [JsoupChapterParser] (parsing HTML → BookBlock).
 *
 * ## Architecture
 *
 * - **Readium** : ouvre le ZIP, expose les `InputStream` des ressources
 *   du spine. Une seule `Publication` par `publicationId`, réutilisée
 *   pour tous les chapitres de la même publication.
 * - **JsoupChapterParser** : parse le XHTML → [Chapter] avec
 *   [com.inktone.domain.model.BookBlock], styles inline et offsets globaux.
 * - **Cache LRU** : 5 MB par octets (pas par entrées), éviction automatique.
 * - **Dispatcher dédié** : `epub-parser` (2 threads) — pas de contention
 *   avec `Dispatchers.Default` (utilisé par Compose pour la mesure/layout).
 * - **Semaphore(2)** : limite le parallélisme à 2 parses simultanés.
 *
 * ## Cycle de vie
 *
 * - [parseChapter] : parsing synchrone (suspend), avec cache et semaphore.
 * - [preload] : lancement asynchrone, retourne un [Job] annulable.
 * - [invalidate] : ferme la `Publication` Readium et vide le cache pour
 *   une `publicationId` donnée (appelé à la fermeture du lecteur).
 * - [registerPublication] : enregistre le mapping `publicationId → fileUri`
 *   avant tout appel à [parseChapter] (appelé après l'import).
 */
@OptIn(ExperimentalReadiumApi::class)
@Singleton
class EpubChapterParser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsoupParser: JsoupChapterParser,
) : ChapterParser {

    // ---- Readium (initialisation paresseuse) ----

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

    // ---- État interne ----

    /** publicationId → fileUri (peuplé via [registerPublication]). */
    private val publicationFiles = ConcurrentHashMap<String, String>()

    /** publicationId → Publication Readium (ouverte paresseusement). */
    private val openPublications = ConcurrentHashMap<String, Publication>()

    // ---- Cache LRU par octets ----

    private val cache = object : android.util.LruCache<String, Chapter>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Chapter): Int {
            // Estimation : somme des approxByteSize des blocs + sentences
            val blocksSize = when (val c = value.content) {
                is com.inktone.domain.model.ChapterContent.Rich -> c.blocks.sumOf { it.approxByteSize }
                else -> 0
            }
            val sentencesSize = value.sentences.sumOf { it.text.length * 2 }
            return blocksSize + sentencesSize + 128 // overhead fixe
        }
    }

    // ---- Dispatcher + Semaphore ----

    private val parserDispatcher = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "epub-parser").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val semaphore = Semaphore(2)

    /** Protège l'ouverture paresseuse des [Publication] (race condition). */
    private val openMutex = Mutex()

    // ---- API publique (ChapterParser) ----

    /**
     * Enregistre le mapping [publicationId] → [fileUri].
     *
     * Doit être appelé après l'import, avant tout [parseChapter] pour
     * cette publication. Idempotent : un appel ultérieur écrase l'ancien.
     */
    override fun registerPublication(publicationId: String, fileUri: String) {
        publicationFiles[publicationId] = fileUri
    }

    override suspend fun parseChapter(
        publicationId: String,
        chapterHref: String,
        fragment: String?,
    ): Chapter {
        val cacheKey = "$publicationId:$chapterHref${fragment?.let { ":$it" } ?: ""}"
        cache.get(cacheKey)?.let { return it }

        return semaphore.withPermit {
            withContext(parserDispatcher) {
                val publication = getOrOpenPublication(publicationId)
                val stream = openChapterStream(publication, chapterHref)
                val chapterIndex = publication.readingOrder.indexOfFirst {
                    it.href.toString() == chapterHref
                }.coerceAtLeast(0)

                val chapter = jsoupParser.parse(
                    inputStream = stream,
                    baseUrl = chapterHref,
                    chapterIndex = chapterIndex,
                    chapterHref = chapterHref,
                    fragment = fragment,
                )
                cache.put(cacheKey, chapter)
                chapter
            }
        }
    }

    override fun preload(
        publicationId: String,
        chapterHref: String,
        scope: CoroutineScope,
    ): Job = scope.launch(parserDispatcher) {
        semaphore.withPermit {
            val cacheKey = "$publicationId:$chapterHref"
            if (cache.get(cacheKey) != null) return@withPermit

            val publication = getOrOpenPublication(publicationId)
            val stream = openChapterStream(publication, chapterHref)
            val chapterIndex = publication.readingOrder.indexOfFirst {
                it.href.toString() == chapterHref
            }.coerceAtLeast(0)

            val chapter = jsoupParser.parse(
                inputStream = stream,
                baseUrl = chapterHref,
                chapterIndex = chapterIndex,
                chapterHref = chapterHref,
            )
            cache.put(cacheKey, chapter)
        }
    }

    override fun invalidate(publicationId: String) {
        // Supprimer toutes les entrées du cache pour cette publication
        val prefix = "$publicationId:"
        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }

        // Fermer la Publication Readium
        openPublications.remove(publicationId)?.close()
        publicationFiles.remove(publicationId)
    }

    // ---- Interne ----

    /**
     * Retourne la [Publication] Readium ouverte pour [publicationId],
     * en l'ouvrant paresseusement si nécessaire.
     */
    private suspend fun getOrOpenPublication(publicationId: String): Publication {
        openPublications[publicationId]?.let { return it }

        val fileUri = publicationFiles[publicationId]
            ?: throw IllegalStateException(
                "Publication $publicationId non enregistrée — appeler registerPublication() d'abord",
            )

        // Mutex évite la race condition : deux appels concurrents pour le même
        // publicationId ne peuvent pas ouvrir deux Publications simultanément.
        return openMutex.withLock {
            // Double-check après acquisition du lock
            openPublications[publicationId]?.let { return@withLock it }

            withContext(parserDispatcher) {
                openPublication(fileUri).also { pub ->
                    openPublications[publicationId] = pub
                }
            }
        }
    }

    /**
     * Ouvre un [ByteArrayInputStream] sur la ressource XHTML d'un chapitre.
     */
    private suspend fun openChapterStream(publication: Publication, chapterHref: String): ByteArrayInputStream {
        val link = publication.readingOrder.find { it.href.toString() == chapterHref }
            ?: throw IllegalStateException("Chapitre non trouvé dans le readingOrder : $chapterHref")

        val resource: Resource = publication.get(link)
            ?: throw IllegalStateException("Ressource non trouvée dans l'EPUB : $chapterHref")

        val length = resource.length().getOrElse {
            throw IllegalStateException("Impossible de lire la longueur de la ressource : $chapterHref")
        }.toLong()

        val bytes = resource.read(0L until length).getOrElse {
            throw IllegalStateException("Impossible de lire la ressource : $chapterHref")
        }

        return ByteArrayInputStream(bytes)
    }

    /**
     * Ouvre un EPUB via Readium et retourne la [Publication].
     */
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

    private companion object {
        /** 5 MB en octets. */
        const val MAX_CACHE_BYTES = 5 * 1024 * 1024
    }
}
