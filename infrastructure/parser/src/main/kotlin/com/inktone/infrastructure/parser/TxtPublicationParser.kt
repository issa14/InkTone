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
            ?.use { stream -> runCatching { decodeText(stream.readBytes()) }.getOrNull() }
            ?: return ParseResult.Corrupted("Fichier introuvable ou illisible (encodage ?): $fileUri")

        if (text.isBlank()) return ParseResult.Corrupted("Fichier TXT vide")

        val trimmedText = text.trim()
        // Audit UX D21 : un paragraphe par bloc, plus le livre entier en un
        // seul — en défilement, la position se déduit du premier bloc visible,
        // qui restait toujours le bloc 0 : ni progression ni reprise ne
        // suivaient la lecture. Le texte et ses offsets sont inchangés
        // (positions, signets et index déjà enregistrés restent valables) ;
        // les phrases sont découpées paragraphe par paragraphe, jamais à
        // cheval sur deux blocs.
        val paragraphs = paragraphRanges(trimmedText)
        var sentenceIndex = 0
        val sentences = paragraphs.flatMapIndexed { blockIndex, range ->
            FrenchSentenceSplitter.split(trimmedText.substring(range)).map { (sentenceText, start, end) ->
                Sentence(
                    index = sentenceIndex++,
                    text = sentenceText,
                    startOffset = range.first + start,
                    endOffset = range.first + end,
                    blockIndex = blockIndex,
                )
            }
        }

        val fileName = fileStorageService.getFileName(fileUri) ?: fileUri.substringAfterLast('/')
        val titleWithoutExtension = fileName.substringBeforeLast('.', fileName)
        val chapter = Chapter(
            index = 0,
            href = fileName,
            title = null,
            content = ChapterContent.Rich(
                blocks = paragraphs.map { range ->
                    BookBlock.ParagraphBlock(
                        richText = StyledText.plain(trimmedText.substring(range)),
                        globalOffsetRange = range,
                    )
                },
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

private val BLANK_LINE = Regex("\\r?\\n[ \\t\\r]*\\n")
private val LINE_BREAK = Regex("\\r?\\n")

/**
 * Plages des paragraphes de [text] : séparés par une ligne vide, ou par un
 * simple retour à la ligne si le texte n'en contient aucune (un paragraphe
 * par ligne). Blancs de bord exclus, paragraphes vides ignorés.
 */
internal fun paragraphRanges(text: String): List<IntRange> {
    val separator = if (BLANK_LINE.containsMatchIn(text)) BLANK_LINE else LINE_BREAK
    val ranges = mutableListOf<IntRange>()
    var start = 0
    fun addTrimmed(from: Int, to: Int) {
        var first = from
        var end = to
        while (first < end && text[first].isWhitespace()) first++
        while (end > first && text[end - 1].isWhitespace()) end--
        if (first < end) ranges += first until end
    }
    for (match in separator.findAll(text)) {
        addTrimmed(start, match.range.first)
        start = match.range.last + 1
    }
    addTrimmed(start, text.length)
    return ranges
}

/**
 * Décode un TXT : UTF-16 si une marque d'ordre des octets le dit, UTF-8 sinon,
 * et Windows-1252 si les octets ne sont pas de l'UTF-8 valide (audit UX D22).
 * `toString(UTF_8)` ne lève jamais d'erreur : un TXT en Windows-1252, courant
 * pour les textes français anciens, s'affichait avec des caractères de
 * remplacement. La marque d'ordre des octets n'est jamais gardée dans le texte.
 */
internal fun decodeText(bytes: ByteArray): String {
    fun hasPrefix(vararg prefix: Int) =
        bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it].toByte() }
    return when {
        hasPrefix(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        hasPrefix(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        else -> {
            val start = if (hasPrefix(0xEF, 0xBB, 0xBF)) 3 else 0
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes, start, bytes.size - start)).toString() }
                .getOrElse { String(bytes, start, bytes.size - start, charset("windows-1252")) }
        }
    }
}

