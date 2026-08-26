package com.inktone.infrastructure.parser

import android.util.LruCache
import com.inktone.domain.model.Chapter
import com.inktone.domain.service.ChapterParser
import com.inktone.domain.service.FileStorageService
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chargement paresseux d'une page PDF — pendant exact d'[EpubChapterParser]
 * pour l'autre format à parsing paresseux.
 *
 * **Pourquoi cette classe existe** (mesure device du 2026-08-26) :
 * `PdfPublicationParser.parse` extrayait le texte de TOUTES les pages, et
 * le lecteur le rappelle à chaque ouverture. Mesuré sur ce dépôt, appareil
 * réel (V2206, Snapdragon 680) : *Une colonne de feu* (Ken Follett, 994
 * pages, 25 331 phrases) coûtait **7 970 ms à chaque ouverture du livre**,
 * soit 8 ms par page — dont l'utilisateur n'en lit qu'une. La fixture
 * synthétique `fixture-large.pdf` (220 pages, 165 ms) masquait le problème :
 * quasiment sans texte, elle ne mesurait que l'ouverture de page.
 *
 * Le document PDFium reste ouvert pour la durée de la session de lecture
 * (fermé par [invalidate], comme `EpubChapterParser` relâche sa
 * `Publication` Readium) : une page suivante ne coûte plus qu'un appel JNI,
 * jamais une relecture du fichier.
 *
 * Contexte natif PDFium non thread-safe (vérifié tâche 12.1) : un unique
 * thread sérialise tous les accès, comme dans [PdfPublicationParser] et
 * [PdfPageRendererImpl].
 */
@Singleton
class PdfChapterParser @Inject constructor(
    private val fileStorageService: FileStorageService,
) : ChapterParser {

    private val pdfiumDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val pdfiumCore = PdfiumCore()

    private val fileUris = ConcurrentHashMap<String, String>()

    /** Documents ouverts, accédés UNIQUEMENT depuis [pdfiumDispatcher]. */
    private val openDocuments = mutableMapOf<String, OpenDocument>()

    private val cache = object : LruCache<String, Chapter>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Chapter): Int =
            value.sentences.sumOf { it.text.length * 2 } + 128
    }

    /**
     * `sourceBytes` est retenu volontairement : `newDocument(ByteArray)` fait
     * un `FPDF_LoadMemDocument`, le natif ne garde qu'un pointeur vers le
     * tampon sans en copier le contenu (vérifié par décompilation de
     * `pdfiumandroid-1.0.20` : `PdfDocument` ne porte que `mNativeDocPtr` et
     * un `parcelFileDescriptor`). Le laisser devenir injoignable exposerait
     * à une lecture de mémoire libérée. Même précaution que dans
     * `PdfPageRendererImpl`.
     */
    private class OpenDocument(
        val document: PdfDocument,
        val pageCount: Int,
        @Suppress("unused") val sourceBytes: ByteArray,
    )

    override fun registerPublication(publicationId: String, fileUri: String) {
        fileUris[publicationId] = fileUri
    }

    override suspend fun parseChapter(
        publicationId: String,
        chapterHref: String,
        fragment: String?,
    ): Chapter = withContext(pdfiumDispatcher) {
        val cacheKey = "$publicationId:$chapterHref"
        cache.get(cacheKey)?.let { return@withContext it }

        val pageIndex = pageIndexOf(chapterHref)
            ?: throw IllegalArgumentException("href de page PDF invalide : $chapterHref")

        val open = openDocument(publicationId)
            ?: throw IllegalStateException("PDF introuvable ou illisible pour $publicationId")

        if (pageIndex !in 0 until open.pageCount) {
            throw IllegalArgumentException("page $pageIndex hors bornes (${open.pageCount} pages)")
        }

        val chapter = open.document.openPage(pageIndex).use { page -> page.toChapter(pageIndex) }
        cache.put(cacheKey, chapter)
        chapter
    }

    override fun preload(
        publicationId: String,
        chapterHref: String,
        scope: CoroutineScope,
    ): Job = scope.launch(pdfiumDispatcher) {
        // Best-effort, strictement comme EpubChapterParser.preload : un échec
        // ici ne doit jamais remonter (il ferait tomber tout le preloadScope
        // du lecteur). parseChapter reste le chemin qui surface l'erreur
        // quand l'utilisateur atteint réellement la page.
        try {
            parseChapter(publicationId, chapterHref)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ignoré volontairement
        }
    }

    override fun invalidate(publicationId: String) {
        val prefix = "$publicationId:"
        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }

        // Fermeture native sur le dispatcher PDFium, jamais depuis le thread
        // appelant : `invalidate` est synchrone par contrat, mais le handle
        // natif n'appartient qu'à ce thread.
        val toClose = synchronized(openDocuments) { openDocuments.remove(publicationId) }
        if (toClose != null) {
            CoroutineScope(pdfiumDispatcher).launch {
                runCatching { toClose.document.close() }
            }
        }
        fileUris.remove(publicationId)
    }

    /** Appelé uniquement depuis [pdfiumDispatcher]. */
    private suspend fun openDocument(publicationId: String): OpenDocument? {
        synchronized(openDocuments) { openDocuments[publicationId] }?.let { return it }

        val fileUri = fileUris[publicationId] ?: return null
        val bytes = fileStorageService.openInputStream(fileUri)
            ?.use { stream -> runCatching { stream.readBytes() }.getOrNull() }
            ?: return null

        val document = try {
            pdfiumCore.newDocument(bytes)
        } catch (e: Exception) {
            return null
        }
        val pageCount = document.getPageCount()
        if (pageCount <= 0) {
            document.close()
            return null
        }

        val open = OpenDocument(document, pageCount, bytes)
        synchronized(openDocuments) { openDocuments[publicationId] = open }
        return open
    }

    private companion object {
        /** Une page de texte pèse quelques Ko ; 4 Mo couvrent largement une session. */
        const val MAX_CACHE_BYTES = 4 * 1024 * 1024
    }
}
