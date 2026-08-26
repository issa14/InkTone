package com.inktone.infrastructure.parser

import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.StyledText
import com.inktone.domain.service.CoverExtractionResult
import com.inktone.domain.service.FileStorageService
import com.inktone.domain.service.FrenchSentenceSplitter
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationMetadata
import com.inktone.domain.service.PublicationParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Un fichier TXT est traité comme UN SEUL chapitre. Découpage en phrases
 * par [FrenchSentenceSplitter] — le découpeur unifié (BreakIterator FR +
 * filtre d'abréviations), identique à l'EPUB (JsoupChapterParser) et au
 * PDF (PdfTextExtraction). Lot 21 : la regex naïve `(?<=[.!?])\s+` cassait
 * les abréviations françaises (M., Dr., etc.) et dégradait le TTS et le
 * surlignage mot-à-mot — source unique pour les trois formats (spike
 * docs/spikes/sentence-tokenizer-comparison.md).
 *
 * Bug réel corrigé (lot 2a) : `fileUri` est une URI SAF `content://`, pas
 * un chemin de fichier local — `java.io.File(fileUri)` ne l'ouvre jamais
 * (`exists()` faux), donc tout TXT importé depuis le vrai sélecteur de
 * fichiers échouait silencieusement en `Corrupted`. Passe désormais par
 * [FileStorageService], seule voie correcte pour lire une URI SAF (K5).
 */
@Singleton
class TxtPublicationParser @Inject constructor(
    private val fileStorageService: FileStorageService,
) : PublicationParser {

    override val supportedFormats = listOf(PublicationFormat.TXT)

    // Lot 19 — un TXT n'a pas de couverture : résultat valide, jamais un
    // échec, jamais un écrasement fautif de la couverture existante.
    override suspend fun extractCover(fileUri: String): CoverExtractionResult =
        CoverExtractionResult.Success(null)

    override suspend fun parse(fileUri: String): ParseResult {
        val text = fileStorageService.openInputStream(fileUri)
            ?.use { stream -> runCatching { stream.readBytes().toString(Charsets.UTF_8) }.getOrNull() }
            ?: return ParseResult.Corrupted("Fichier introuvable ou illisible (encodage ?): $fileUri")

        if (text.isBlank()) return ParseResult.Corrupted("Fichier TXT vide")

        val trimmedText = text.trim()
        // blockIndex = 0 : le chapitre unique produit toujours exactement
        // un BookBlock.ParagraphBlock (ci-dessous) — jamais le défaut -1,
        // sinon l'auto-scroll TTS (ReaderScreen) ne trouve jamais son bloc.
        // Les offsets (startOffset/endOffset) sont réels, dans l'espace du
        // trimmedText, garantis par FrenchSentenceSplitter (contrat
        // d'offsets stables : substring == phrase).
        val sentences = FrenchSentenceSplitter.split(trimmedText).mapIndexed { index, (text, startOffset, endOffset) ->
            Sentence(index = index, text = text, startOffset = startOffset, endOffset = endOffset, blockIndex = 0)
        }

        val fileName = fileStorageService.getFileName(fileUri) ?: fileUri.substringAfterLast('/')
        val titleWithoutExtension = fileName.substringBeforeLast('.', fileName)
        val chapter = Chapter(
            index = 0,
            href = fileName,
            title = null,
            content = ChapterContent.Rich(
                blocks = listOf(
                    BookBlock.ParagraphBlock(
                        richText = StyledText.plain(trimmedText),
                        globalOffsetRange = 0 until trimmedText.length,
                    ),
                ),
            ),
            sentences = sentences,
        )
        return ParseResult.Success(
            documentModel = DocumentModel(chapters = listOf(chapter), tableOfContents = emptyList(), resources = emptyList()),
            isDrmProtected = false, // TXT n'a jamais de DRM par définition
            // Un TXT n'a pas de metadonnees embarquees (pas d'OPF) — seul le
            // nom de fichier est disponible comme titre.
            metadata = PublicationMetadata(title = titleWithoutExtension),
        )
    }
}
