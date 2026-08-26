package com.inktone.infrastructure.parser

import android.graphics.Bitmap
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.service.CoverExtractionResult
import com.inktone.domain.service.FileStorageService
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationMetadata
import com.inktone.domain.service.PublicationParser
import io.legere.pdfiumandroid.PdfPasswordException
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Un PDF est traite comme N pages, chacune un [Chapter] a part entiere
 * (Lot 12, tache 12.2/12.3, decision actee 4 du plan).
 *
 * **[parse] est PARESSEUX depuis la mesure device du 2026-08-26** : il rend
 * des coquilles de pages, le texte etant charge page par page par
 * [PdfChapterParser] — meme modele que l'EPUB
 * (`ReadiumPublicationParser.parseLazy` + `EpubChapterParser`). Extraire
 * tout le livre ici coutait 7 970 ms A CHAQUE OUVERTURE pour un roman de
 * 994 pages (Snapdragon 680), pour une seule page effectivement lue.
 * L'import, lui, a bien besoin de tout : il passe par [parseAllPages].
 *
 * Version PDFium retenue : `io.legere:pdfiumandroid:1.0.20`, pas la
 * derniere publiee - `2.0.3`/`2.0.2`/`1.0.35` embarquent une metadonnee
 * Kotlin incompatible avec le plugin Kotlin 2.0.20 de ce projet (verifie
 * par build reel, tache 12.1), pas 1.0.20.
 */
@Singleton
class PdfPublicationParser @Inject constructor(
    private val fileStorageService: FileStorageService,
    private val coverStorage: CoverStorage,
) : PublicationParser {

    override val supportedFormats = listOf(PublicationFormat.PDF)

    // Contexte natif PDFium non thread-safe (verifie tache 12.1) - un seul
    // thread dedie serialise tous les acces JNI de ce parser, jamais
    // Dispatchers.IO directement (pool multi-thread).
    private val pdfiumDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val pdfiumCore = PdfiumCore()

    override suspend fun parse(fileUri: String): ParseResult = withContext(pdfiumDispatcher) {
        val bytes = fileStorageService.openInputStream(fileUri)
            ?.use { stream -> runCatching { stream.readBytes() }.getOrNull() }
            ?: return@withContext ParseResult.Corrupted("Fichier introuvable ou illisible : $fileUri")

        if (!hasPdfMagicBytes(bytes)) {
            return@withContext ParseResult.UnsupportedFormat("Signature PDF absente (extension usurpee ?)")
        }

        val document = try {
            pdfiumCore.newDocument(bytes)
        } catch (e: PdfPasswordException) {
            return@withContext ParseResult.DrmProtected("PDF protege par mot de passe : $fileUri")
        } catch (e: Exception) {
            // safeNativeCall (tache 12.2) : un pointeur nul ou une erreur
            // native (fichier tronque, structure invalide) ne doit jamais
            // faire planter le process (SIGSEGV) - convertie en resultat
            // type, jamais une exception qui remonte au hasard (Blueprint
            // §7.11).
            return@withContext ParseResult.Corrupted("PDF illisible ou corrompu : ${e.message}")
        }

        try {
            val pageCount = document.getPageCount()
            if (pageCount <= 0) {
                return@withContext ParseResult.Corrupted("PDF sans page exploitable : $fileUri")
            }

            // Coquilles de pages, contenu chargé à la demande par
            // [PdfChapterParser] — voir le KDoc de classe. Seules les
            // PROBE_PAGES premières pages sont extraites ici, et uniquement
            // pour que le lecteur sache s'il a du texte a narrer
            // (`ReaderUiState.supportsTts`) sans payer le livre entier.
            val probeUntil = minOf(PROBE_PAGES, pageCount)
            val chapters = (0 until pageCount).map { pageIndex ->
                if (pageIndex < probeUntil) {
                    document.openPage(pageIndex).use { page -> page.toChapter(pageIndex) }
                } else {
                    pageShell(pageIndex)
                }
            }

            val meta = runCatching { document.getDocumentMeta() }.getOrNull()
            val coverUri = extractAndSaveCover(document, fileUri)

            ParseResult.Success(
                documentModel = DocumentModel(chapters = chapters, tableOfContents = emptyList(), resources = emptyList()),
                isDrmProtected = false,
                metadata = PublicationMetadata(
                    title = meta?.title?.takeIf { it.isNotBlank() },
                    authors = meta?.author?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList(),
                    coverUri = coverUri,
                ),
            )
        } finally {
            document.close()
        }
    }

    /**
     * Extrait le texte de TOUTES les pages — le seul appelant légitime est
     * l'import, qui doit alimenter l'index de recherche
     * (`ImportPublicationUseCase`). Jamais le lecteur : c'est précisément ce
     * coût (7 970 ms pour 994 pages, mesuré sur appareil) que le parsing
     * paresseux de [parse] supprime.
     */
    suspend fun parseAllPages(fileUri: String): List<Chapter> = withContext(pdfiumDispatcher) {
        val bytes = fileStorageService.openInputStream(fileUri)
            ?.use { stream -> runCatching { stream.readBytes() }.getOrNull() }
            ?: return@withContext emptyList()
        if (!hasPdfMagicBytes(bytes)) return@withContext emptyList()

        val document = try {
            pdfiumCore.newDocument(bytes)
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        try {
            (0 until document.getPageCount()).map { pageIndex ->
                document.openPage(pageIndex).use { page -> page.toChapter(pageIndex) }
            }
        } finally {
            document.close()
        }
    }

    private fun hasPdfMagicBytes(bytes: ByteArray): Boolean {
        val signature = "%PDF-".toByteArray(Charsets.US_ASCII)
        if (bytes.size < signature.size) return false
        return bytes.copyOfRange(0, signature.size).contentEquals(signature)
    }

    /**
     * Lot 19 — ré-extrait la couverture (page 0) sans re-parser le texte.
     * Un fichier illisible, sans signature PDF ou protégé retourne
     * [CoverExtractionResult.Failure] (jamais une exception qui remonte,
     * jamais un écrasement de la couverture existante par l'appelant).
     */
    override suspend fun extractCover(fileUri: String): CoverExtractionResult = withContext(pdfiumDispatcher) {
        val bytes = fileStorageService.openInputStream(fileUri)
            ?.use { stream -> runCatching { stream.readBytes() }.getOrNull() }
            ?: return@withContext CoverExtractionResult.Failure

        if (!hasPdfMagicBytes(bytes)) return@withContext CoverExtractionResult.Failure

        val document = try {
            pdfiumCore.newDocument(bytes)
        } catch (e: Exception) {
            return@withContext CoverExtractionResult.Failure
        }

        try {
            CoverExtractionResult.Success(extractAndSaveCover(document, fileUri))
        } finally {
            document.close()
        }
    }

    /**
     * Rendu de la page 0 via la primitive partagee [renderToBitmap]
     * (tache 12.7 - reutilisee par `PdfPageRendererImpl` au Palier 2, un
     * seul point d'appel a l'API bitmap PDFium). Sauvegarde au meme
     * format et au meme emplacement que la couverture EPUB
     * ([ReadiumPublicationParser.extractAndSaveCover]) - JPEG qualite 85
     * via [CoverStorage] (`filesDir/covers/`), pas WEBP comme envisage
     * dans la recherche : un seul format de couverture dans l'app plutot
     * que deux conventions divergentes sans raison forte.
     */
    private fun extractAndSaveCover(document: io.legere.pdfiumandroid.PdfDocument, fileUri: String): String? =
        try {
            document.openPage(0).use { page ->
                val bitmap = page.renderToBitmap(COVER_TARGET_WIDTH_PX) ?: return@use null

                val coverFile = coverStorage.coverFileFor(fileUri)
                try {
                    FileOutputStream(coverFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
                    coverFile.absolutePath
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("PdfPublicationParser", "Echec sauvegarde couverture pour $fileUri", e)
            null
        }

    private companion object {
        const val COVER_TARGET_WIDTH_PX = 300

        /**
         * Nombre de pages réellement extraites par [parse]. Assez pour
         * décider si le document porte du texte (donc si le TTS a un sens),
         * assez peu pour rester imperceptible : ~8 ms/page sur Snapdragon 680.
         */
        const val PROBE_PAGES = 5
    }
}
