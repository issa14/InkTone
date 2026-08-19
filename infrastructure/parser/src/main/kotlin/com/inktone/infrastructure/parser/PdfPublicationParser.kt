package com.inktone.infrastructure.parser

import android.content.Context
import android.graphics.Bitmap
import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.StyledText
import com.inktone.domain.service.CoverExtractionResult
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

            val chapters = (0 until pageCount).map { pageIndex ->
                document.openPage(pageIndex).use { page ->
                    val (fullText, sentences) = extractPageContent(page)
                    val blocks = if (fullText.isNotBlank()) {
                        listOf(
                            BookBlock.ParagraphBlock(
                                richText = StyledText.plain(fullText),
                                globalOffsetRange = 0 until fullText.length,
                            ),
                        )
                    } else {
                        emptyList<BookBlock>()
                    }
                    Chapter(
                        index = pageIndex,
                        href = "page-$pageIndex",
                        title = null,
                        content = ChapterContent.Rich(blocks = blocks),
                        sentences = sentences,
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
     * Extrait le texte complet et les phrases d'une page PDF.
     *
     * @return Pair(texte complet trimé, liste de phrases avec offsets).
     *   Texte vide si la page est une image scannée sans texte.
     */
    private fun extractPageContent(page: PdfPage): Pair<String, List<Sentence>> = page.openTextPage().use { textPage ->
        val charCount = textPage.textPageCountChars()
        if (charCount <= 0) return@use "" to emptyList()
        val text = textPage.textPageGetText(0, charCount)?.trim()
        if (text.isNullOrBlank()) return@use "" to emptyList()

        var offset = 0
        val sentences = sentenceBoundary.split(text).mapIndexed { index, raw ->
            val trimmed = raw.trim()
            // blockIndex = 0 : la page produit toujours exactement un
            // BookBlock.ParagraphBlock unique (ci-dessous) quand du texte
            // existe — jamais le défaut -1, sinon l'auto-scroll TTS
            // (ReaderScreen) ne trouve jamais son bloc pour un PDF.
            val sentence = Sentence(index = index, text = trimmed, startOffset = offset, endOffset = offset + trimmed.length, blockIndex = 0)
            offset += trimmed.length + 1
            sentence
        }.filter { it.text.isNotBlank() }
        text to sentences
    }

    /**
     * Rendu de la page 0 via la primitive partagee [renderToBitmap]
     * (tache 12.7 - reutilisee par `PdfPageRendererImpl` au Palier 2, un
     * seul point d'appel a l'API bitmap PDFium). Sauvegarde au meme
     * format et au meme emplacement que la couverture EPUB
     * ([ReadiumPublicationParser.extractAndSaveCover]) - JPEG qualite 85
     * dans `context.cacheDir/covers/`, pas WEBP comme envisage initialement
     * dans la recherche : un seul format de couverture dans l'app plutot
     * que deux conventions divergentes sans raison forte.
     */
    private fun extractAndSaveCover(document: io.legere.pdfiumandroid.PdfDocument, fileUri: String): String? =
        try {
            document.openPage(0).use { page ->
                val bitmap = page.renderToBitmap(COVER_TARGET_WIDTH_PX) ?: return@use null

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
        } catch (e: Exception) {
            android.util.Log.w("PdfPublicationParser", "Echec sauvegarde couverture pour $fileUri", e)
            null
        }

    private companion object {
        const val COVER_TARGET_WIDTH_PX = 300
    }
}
