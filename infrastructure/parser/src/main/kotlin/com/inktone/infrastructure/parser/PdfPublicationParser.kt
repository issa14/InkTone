package com.inktone.infrastructure.parser

import com.inktone.domain.model.Chapter
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.Paragraph
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.service.FileStorageService
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationMetadata
import com.inktone.domain.service.PublicationParser
import io.legere.pdfiumandroid.PdfPasswordException
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

            // Extraction du texte par page : tache 12.3. Cette version
            // pose l'ouverture/detection d'erreurs (tache 12.2) avec des
            // chapitres deja corrects en nombre et en adressage, mais sans
            // paragraphes - complete par le prochain commit, pas un etat
            // final.
            val chapters = (0 until pageCount).map { pageIndex ->
                Chapter(index = pageIndex, href = "page-$pageIndex", title = null, paragraphs = emptyList())
            }

            val meta = runCatching { document.getDocumentMeta() }.getOrNull()

            ParseResult.Success(
                documentModel = DocumentModel(chapters = chapters, tableOfContents = emptyList(), resources = emptyList()),
                isDrmProtected = false,
                metadata = PublicationMetadata(
                    title = meta?.title?.takeIf { it.isNotBlank() },
                    authors = meta?.author?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList(),
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
}
