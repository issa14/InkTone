package com.inktone.infrastructure.parser

import com.inktone.domain.model.RenderedPage
import com.inktone.domain.service.FileStorageService
import com.inktone.domain.service.FixedPageDocument
import com.inktone.domain.service.FixedPageOpenResult
import com.inktone.domain.service.FixedPageRenderer
import io.legere.pdfiumandroid.PdfPasswordException
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémente [FixedPageRenderer] via PDFium (Lot 12, tâche 12.7) — même
 * binding que [PdfPublicationParser], mais cycle de vie distinct
 * (décision actée 14 du plan) : [PdfPublicationParser.parse] ouvre,
 * extrait, ferme en un aller-retour ; le [FixedPageDocument] produit ici
 * reste ouvert pour toute la session de lecture, fermé explicitement par
 * l'appelant.
 */
@Singleton
class PdfPageRendererImpl @Inject constructor(
    private val fileStorageService: FileStorageService,
) : FixedPageRenderer {

    // Contexte natif PDFium non thread-safe (verifie tache 12.1) — meme
    // discipline que PdfPublicationParser : dispatcher a un seul thread
    // dedie a ce renderer, distinct de celui du parser (les deux ouvrent
    // des documents PDFium independants, jamais partages).
    private val pdfiumDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val pdfiumCore = PdfiumCore()

    override suspend fun open(fileUri: String): FixedPageOpenResult = withContext(pdfiumDispatcher) {
        val bytes = fileStorageService.openInputStream(fileUri)
            ?.use { stream -> runCatching { stream.readBytes() }.getOrNull() }
            ?: return@withContext FixedPageOpenResult.Failed("Fichier introuvable ou illisible : $fileUri")

        val document = try {
            pdfiumCore.newDocument(bytes)
        } catch (e: PdfPasswordException) {
            return@withContext FixedPageOpenResult.Failed("PDF protege par mot de passe : $fileUri")
        } catch (e: Exception) {
            // safeNativeCall (meme discipline que la tache 12.2) : jamais
            // un crash natif remonte tel quel.
            return@withContext FixedPageOpenResult.Failed("PDF illisible ou corrompu : ${e.message}")
        }

        val pageCount = document.getPageCount()
        if (pageCount <= 0) {
            document.close()
            return@withContext FixedPageOpenResult.Failed("PDF sans page exploitable : $fileUri")
        }

        // `bytes` est passe TEL QUEL au natif (voir KDoc de
        // PdfFixedPageDocument) : il doit survivre au retour de `open`.
        FixedPageOpenResult.Success(PdfFixedPageDocument(document, pageCount, pdfiumDispatcher, bytes))
    }
}

/**
 * **Bug reel trouve sur appareil (2026-08-26) : PDF ouvert = ecran noir.**
 *
 * `PdfiumCore.newDocument(ByteArray)` fait un `FPDF_LoadMemDocument` : le
 * natif conserve un POINTEUR vers le tableau, il n'en copie pas le
 * contenu. Verifie par decompilation de `pdfiumandroid-1.0.20` —
 * `PdfDocument` ne porte que `mNativeDocPtr` et un champ
 * `parcelFileDescriptor` (retenu, lui, pour la surcharge PFD) : AUCUN
 * champ ne retient le `ByteArray`. C'est donc a l'appelant de le
 * maintenir en vie aussi longtemps que le document.
 *
 * [PdfPublicationParser.parse] y survivait par accident : son `bytes` est
 * une locale vivante jusqu'au `document.close()` du `finally`. Ici, `open`
 * RETOURNE et rendait le tableau injoignable — chaque `renderPage`
 * suivant lisait de la memoire liberee, echouait, et l'echec disparaissait
 * dans le `catch` ci-dessous. D'ou le symptome exact remonte par les
 * beta-testeurs : import, titre, auteur et couverture corrects (tous
 * produits par le parser), mais page noire et muette a la lecture.
 *
 * [sourceBytes] n'est jamais lu par ce code : sa seule raison d'etre est
 * d'empecher le ramasse-miettes de collecter le tampon que PDFium lit.
 * Ne pas le supprimer parce qu'il "ne sert a rien".
 */
private class PdfFixedPageDocument(
    private val document: io.legere.pdfiumandroid.PdfDocument,
    override val pageCount: Int,
    private val pdfiumDispatcher: CoroutineDispatcher,
    @Suppress("unused") private val sourceBytes: ByteArray,
) : FixedPageDocument {

    override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): RenderedPage? = withContext(pdfiumDispatcher) {
        if (pageIndex !in 0 until pageCount) return@withContext null
        try {
            document.openPage(pageIndex).use { page ->
                val bitmap = page.renderToBitmap(targetWidthPx) ?: return@use null
                try {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    RenderedPage(widthPx = bitmap.width, heightPx = bitmap.height, pixelsArgb = pixels)
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            // Meme discipline safeNativeCall : un echec de rendu ponctuel
            // (page corrompue isolement, erreur native transitoire) ne
            // doit jamais crasher la session de lecture en cours. Mais il
            // ne doit plus disparaitre sans laisser de trace : c'est ce
            // silence qui a rendu le bug du tampon collecte (voir KDoc de
            // cette classe) invisible jusqu'a la verification sur appareil.
            android.util.Log.w("PdfPageRenderer", "echec de rendu de la page $pageIndex", e)
            null
        }
    }

    override fun close() {
        document.close()
    }
}
