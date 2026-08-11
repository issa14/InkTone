package com.inktone.infrastructure.parser

import android.content.Context
import android.graphics.Bitmap
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.Paragraph
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.Sentence
import com.inktone.domain.service.FileStorageService
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationMetadata
import com.inktone.domain.service.PublicationParser
import dagger.hilt.android.qualifiers.ApplicationContext
import io.legere.pdfiumandroid.PdfPage
import io.legere.pdfiumandroid.PdfPasswordException
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Un PDF est traite comme N pages, chacune un [Chapter] a part entiere
 * (Lot 12, tache 12.2/12.3, decision actee 4 du plan) - jamais un
 * `DocumentModel` vide de facade : les `paragraphs` viennent du texte
 * reellement extrait de la page, liste vide seulement si la page est une
 * image scannee sans texte.
 *
 * Version PDFium retenue : `io.legere:pdfiumandroid:1.0.20`, pas la
 * derniere publiee - `2.0.3`/`2.0.2`/`1.0.35` embarquent une metadonnee
 * Kotlin incompatible avec le plugin Kotlin 2.0.20 de ce projet (verifie
 * par build reel, tache 12.1), pas 1.0.20.
 */
@Singleton
class PdfPublicationParser @Inject constructor(
    private val fileStorageService: FileStorageService,
    @ApplicationContext private val context: Context,
) : PublicationParser {

    override val supportedFormats = listOf(PublicationFormat.PDF)

    // Contexte natif PDFium non thread-safe (verifie tache 12.1) - un seul
    // thread dedie serialise tous les acces JNI de ce parser, jamais
    // Dispatchers.IO directement (pool multi-thread).
    private val pdfiumDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val pdfiumCore = PdfiumCore()

    // Meme regex naive que TxtPublicationParser (Tache 4.2) - decoupage
    // linguistique reel hors perimetre d'un parser (Blueprint §8.6).
    private val sentenceBoundary = Regex("""(?<=[.!?])\s+""")

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

            // Un Chapter par page (decision actee 4 du plan) - paragraphs
            // vient du texte reellement extrait via PdfTextPage, liste
            // vide si la page est une image scannee sans texte (jamais un
            // objet vide de facade pour les autres pages).
            val chapters = (0 until pageCount).map { pageIndex ->
                document.openPage(pageIndex).use { page ->
                    val sentences = extractSentences(page)
                    Chapter(
                        index = pageIndex,
                        href = "page-$pageIndex",
                        title = null,
                        paragraphs = if (sentences.isEmpty()) emptyList() else listOf(Paragraph(0, sentences)),
                    )
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

    private fun hasPdfMagicBytes(bytes: ByteArray): Boolean {
        val signature = "%PDF-".toByteArray(Charsets.US_ASCII)
        if (bytes.size < signature.size) return false
        return bytes.copyOfRange(0, signature.size).contentEquals(signature)
    }

    /**
     * Texte reellement extrait de la page via l'API texte de PDFium
     * (`PdfTextPage`/`FPDFText_*`) - directive Issa (Lot 12) : indispensable
     * des ce palier pour que le `Locator` (page = chapterIndex, charOffset
     * dans ce texte) soit deja la structure que Sherpa-ONNX consommera au
     * lot TTS futur, jamais une extraction differee. Liste vide si la page
     * n'a aucun texte extractible (image scannee).
     */
    private fun extractSentences(page: PdfPage): List<Sentence> = page.openTextPage().use { textPage ->
        val charCount = textPage.textPageCountChars()
        if (charCount <= 0) return@use emptyList()
        val text = textPage.textPageGetText(0, charCount)?.trim()
        if (text.isNullOrBlank()) return@use emptyList()

        var offset = 0
        sentenceBoundary.split(text).mapIndexed { index, raw ->
            val trimmed = raw.trim()
            val sentence = Sentence(index = index, text = trimmed, startOffset = offset, endOffset = offset + trimmed.length)
            offset += trimmed.length + 1
            sentence
        }.filter { it.text.isNotBlank() }
    }

    /**
     * Rendu de la page 0 en bitmap basse resolution, sauvegarde au meme
     * format et au meme emplacement que la couverture EPUB
     * ([ReadiumPublicationParser.extractAndSaveCover]) - JPEG qualite 85
     * dans `context.cacheDir/covers/`, pas WEBP comme envisage initialement
     * dans la recherche : un seul format de couverture dans l'app plutot
     * que deux conventions divergentes sans raison forte.
     */
    private fun extractAndSaveCover(document: io.legere.pdfiumandroid.PdfDocument, fileUri: String): String? =
        try {
            document.openPage(0).use { page ->
                val widthPt = page.getPageWidthPoint()
                val heightPt = page.getPageHeightPoint()
                if (widthPt <= 0 || heightPt <= 0) {
                    null
                } else {
                    val targetWidth = 300
                    val targetHeight = (targetWidth.toFloat() * heightPt / widthPt).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    page.renderPageBitmap(bitmap, 0, 0, targetWidth, targetHeight, false, false)

                    val coverDir = File(context.cacheDir, "covers")
                    coverDir.mkdirs()
                    val coverFile = File(coverDir, "${fileUri.hashCode().toUInt()}.jpg")
                    try {
                        FileOutputStream(coverFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
                        coverFile.absolutePath
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("PdfPublicationParser", "Echec sauvegarde couverture pour $fileUri", e)
            null
        }
}
