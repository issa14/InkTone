package com.inktone.infrastructure.parser

import com.inktone.domain.model.Chapter
import com.inktone.domain.service.ChapterParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.resource.Resource
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémente [ChapterParser] pour les EPUB via Readium (accès aux
 * ressources ZIP) et [JsoupChapterParser] (parsing HTML → BookBlock).
 *
 * ## Architecture
 *
 * - **[ReadiumPublicationRegistry]** : ouvre le ZIP et expose les
 *   `InputStream` des ressources du spine — partagé avec
 *   [ReadiumResourceResolver] (K2 : une seule `Publication` Readium par
 *   `publicationId`, jamais une par consommateur).
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
 * - [invalidate] : ferme la `Publication` Readium (via le registre partagé)
 *   et vide le cache pour une `publicationId` donnée (appelé à la
 *   fermeture du lecteur).
 * - [registerPublication] : enregistre le mapping `publicationId → fileUri`
 *   avant tout appel à [parseChapter] (appelé après l'import).
 */
@OptIn(ExperimentalReadiumApi::class)
@Singleton
class EpubChapterParser @Inject constructor(
    private val registry: ReadiumPublicationRegistry,
    private val jsoupParser: JsoupChapterParser,
) : ChapterParser {

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

    // ---- API publique (ChapterParser) ----

    /**
     * Enregistre le mapping [publicationId] → [fileUri].
     *
     * Doit être appelé après l'import, avant tout [parseChapter] pour
     * cette publication. Idempotent : un appel ultérieur écrase l'ancien.
     */
    override fun registerPublication(publicationId: String, fileUri: String) {
        registry.register(publicationId, fileUri)
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
                val publication = registry.getOrOpen(publicationId)
                val link = resolveChapterLink(publication, chapterHref)
                val stream = openResourceStream(publication, link, chapterHref)
                val chapterIndex = publication.readingOrder.indexOf(link).coerceAtLeast(0)

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

            // Best-effort : un échec de préchargement ne doit jamais
            // remonter (structured concurrency ferait planter tout
            // preloadScope, cf. ReaderViewModel.preloadAdjacentChapters).
            // parseChapter() est le chemin qui surface l'erreur pour de
            // vrai quand l'utilisateur navigue effectivement vers ce
            // chapitre.
            val chapter = try {
                val publication = registry.getOrOpen(publicationId)
                val link = resolveChapterLink(publication, chapterHref)
                val stream = openResourceStream(publication, link, chapterHref)
                val chapterIndex = publication.readingOrder.indexOf(link).coerceAtLeast(0)

                jsoupParser.parse(
                    inputStream = stream,
                    baseUrl = chapterHref,
                    chapterIndex = chapterIndex,
                    chapterHref = chapterHref,
                )
            } catch (e: CancellationException) {
                throw e // ne jamais avaler l'annulation coopérative (D9)
            } catch (e: Exception) {
                return@withPermit
            }
            cache.put(cacheKey, chapter)
        }
    }

    override fun invalidate(publicationId: String) {
        // Supprimer toutes les entrées du cache pour cette publication
        val prefix = "$publicationId:"
        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }

        registry.release(publicationId)
    }

    // ---- Interne ----

    /**
     * Résout [chapterHref] en [Link] Readium via [Url] + [Publication.linkWithHref]
     * — normalise le percent-encoding (K6, CLAUDE.md) au lieu d'une
     * comparaison de chaîne brute contre [Publication.readingOrder].
     * (`resourceWithHref(String)` fait la même chose mais est dépréciée en
     * 3.0.0 ; `Url.fromEpubHref` fait aussi la même chose mais est une API
     * interne Readium, non appelable ici.)
     */
    private fun resolveChapterLink(publication: Publication, chapterHref: String): Link {
        val url = Url(chapterHref)
            ?: throw IllegalStateException("Href de chapitre invalide : $chapterHref")
        return publication.linkWithHref(url)
            ?: throw IllegalStateException("Chapitre non trouvé dans le readingOrder : $chapterHref")
    }

    /**
     * Ouvre un [ByteArrayInputStream] sur la ressource XHTML d'un chapitre.
     */
    private suspend fun openResourceStream(publication: Publication, link: Link, chapterHref: String): ByteArrayInputStream {
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

    private companion object {
        /** 5 MB en octets. */
        const val MAX_CACHE_BYTES = 5 * 1024 * 1024
    }
}
