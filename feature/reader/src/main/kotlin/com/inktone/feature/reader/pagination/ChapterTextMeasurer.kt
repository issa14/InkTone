package com.inktone.feature.reader.pagination

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.feature.reader.rendering.BookBlockStyleMapper

/**
 * Résultat de la mesure d'un chapitre — entier ou d'un préfixe borné
 * (voir `measureFirstPage`). `annotatedString` est réutilisé tel quel
 * par le rendu du mode pagé (3a.2) — tranché par offset selon la page,
 * jamais reconstruit par page ni par mot prononcé (voir l'exigence de
 * coût de recomposition de 3a.2). `sentenceStartOffsets[i]` est
 * l'offset, dans ce même `annotatedString`, où commence la phrase
 * d'index global `i` (même indexation que
 * `chapter.paragraphs.flatMap { it.sentences }`) — sa taille indique
 * combien de phrases, depuis le début du chapitre, sont couvertes par
 * cette mesure : moins que le total pour un préfixe, tout le chapitre
 * sinon.
 */
data class ChapterMeasurement(
    val annotatedString: AnnotatedString,
    val lines: List<LineGeometry>,
    val sentenceStartOffsets: List<Int>,
)

/**
 * Adaptateur Compose du moteur de pagination (Tâche 3a.1, étapes 1-4) :
 * construit l'AnnotatedString du chapitre (ou d'un préfixe, 3a.3) en
 * préservant les `ParagraphStyle` réels de l'EPUB (voir `spanStyleFor` —
 * 3a.2 restaure leur rendu, les conserver ici évite de les reperdre au
 * passage), mesure via `TextMeasurer` avec le `TextStyle` réellement
 * appliqué au rendu, puis extrait la géométrie ligne par ligne du
 * `TextLayoutResult` pour le moteur pur (`VirtualPaginationEngine`).
 *
 * Écart déclaré (3a.1, toujours vrai en 3a.3) : cet adaptateur dépend de
 * `TextMeasurer` (Compose UI / mesure de police Android réelle), donc
 * non exécutable en test JVM pur. Couvert par `ChapterTextMeasurerTest`
 * (Robolectric) — voir son KDoc pour un écart d'environnement distinct
 * (métriques de police dégénérées dans le sandbox de développement).
 */
class ChapterTextMeasurer(private val textMeasurer: TextMeasurer) {

    /** Mesure le chapitre entier. Coût proportionnel à sa longueur. */
    fun measure(chapter: Chapter, baseStyle: TextStyle, maxWidthPx: Int): ChapterMeasurement {
        return measureRich(chapter, baseStyle, maxWidthPx, maxChars = Int.MAX_VALUE)
    }

    fun measureFirstPage(
        chapter: Chapter,
        baseStyle: TextStyle,
        maxWidthPx: Int,
        prefixCharBudget: Int = DEFAULT_PREFIX_CHAR_BUDGET,
    ): ChapterMeasurement {
        return measureRich(chapter, baseStyle, maxWidthPx, maxChars = prefixCharBudget)
    }

    private fun measureBuilt(
        annotatedString: AnnotatedString,
        sentenceStartOffsets: List<Int>,
        baseStyle: TextStyle,
        maxWidthPx: Int,
    ): ChapterMeasurement {
        if (annotatedString.text.isEmpty()) {
            return ChapterMeasurement(annotatedString, emptyList(), sentenceStartOffsets)
        }

        val layoutResult = textMeasurer.measure(
            text = annotatedString,
            style = baseStyle,
            constraints = Constraints(maxWidth = maxWidthPx),
        )

        val lines = (0 until layoutResult.lineCount).map { lineIndex ->
            LineGeometry(
                top = layoutResult.getLineTop(lineIndex),
                bottom = layoutResult.getLineBottom(lineIndex),
                startOffset = layoutResult.getLineStart(lineIndex),
                endOffset = layoutResult.getLineEnd(lineIndex, visibleEnd = true),
            )
        }

        return ChapterMeasurement(annotatedString, lines, sentenceStartOffsets)
    }

    // ---- Rich measurement (batching, Palier 3.5) ----

    /**
     * Mesure un chapitre [ChapterContent.Rich] par lots de ~10 000
     * caractères pour éviter les crashs de texture Compose sur les longs
     * chapitres (dépassement de la taille maximale de texture GPU).
     *
     * ## Algorithme
     *
     * 1. Découper les [BookBlock.ParagraphBlock] et [BookBlock.HeadingBlock]
     *    en lots de [MAX_BATCH_CHARS] caractères max. Frontières de lot
     *    TOUJOURS entre deux blocs (jamais au milieu).
     * 2. Mesurer chaque lot indépendamment via [measureBuilt].
     * 3. Accumuler les [LineGeometry] avec `top`/`bottom` ajustés
     *    (décalage vertical cumulatif).
     * 4. Accumuler les `sentenceStartOffsets` dans l'espace global
     *    (somme des longueurs de tous les lots précédents).
     *
     * [maxChars] borne le nombre total de caractères mesurés (utilisé
     * par [measureFirstPage]).
     */
    private fun measureRich(
        chapter: Chapter,
        baseStyle: TextStyle,
        maxWidthPx: Int,
        maxChars: Int,
    ): ChapterMeasurement {
        val blocks = (chapter.content as ChapterContent.Rich).blocks
        val textBlocks = blocks.filter {
            it is BookBlock.ParagraphBlock || it is BookBlock.HeadingBlock
        }
        if (textBlocks.isEmpty()) {
            return ChapterMeasurement(AnnotatedString(""), emptyList(), emptyList())
        }

        // 1. Découper en lots
        val batches = buildBatches(textBlocks, maxChars)
        if (batches.isEmpty()) {
            return ChapterMeasurement(AnnotatedString(""), emptyList(), emptyList())
        }

        // 2. Mesurer chaque lot et accumuler
        val allLines = mutableListOf<LineGeometry>()
        val allSentenceOffsets = mutableListOf<Int>()
        var cumulativeTop = 0f
        var globalOffset = 0

        for (batch in batches) {
            val (annotatedString, localOffsets) = buildBatchAnnotatedString(batch)
            val measurement = measureBuilt(annotatedString, localOffsets, baseStyle, maxWidthPx)

            // Ajuster les tops/bottoms des lignes
            for (line in measurement.lines) {
                allLines.add(
                    line.copy(top = line.top + cumulativeTop, bottom = line.bottom + cumulativeTop),
                )
            }

            // Ajuster les offsets de phrase dans l'espace global
            for (offset in measurement.sentenceStartOffsets) {
                allSentenceOffsets.add(offset + globalOffset)
            }

            cumulativeTop += measurement.lines.lastOrNull()?.bottom ?: 0f
            globalOffset += annotatedString.text.length
        }

        // AnnotatedString "virtuel" : seul le premier lot est stocké
        // (le contrat de ChapterMeasurement exige un AnnotatedString,
        // mais le rendu paginé tranche par offset — le premier lot suffit
        // comme référence pour les offsets).
        val firstBatchAnnotated = buildBatchAnnotatedString(batches.first()).first
        return ChapterMeasurement(firstBatchAnnotated, allLines, allSentenceOffsets)
    }

    /**
     * Découpe les blocs de texte en lots de [maxChars] caractères max.
     * Les frontières tombent toujours entre deux blocs.
     */
    private fun buildBatches(
        textBlocks: List<BookBlock>,
        maxChars: Int,
    ): List<List<BookBlock>> {
        val batches = mutableListOf<List<BookBlock>>()
        var currentBatch = mutableListOf<BookBlock>()
        var currentChars = 0

        for (block in textBlocks) {
            val blockLen = when (block) {
                is BookBlock.ParagraphBlock -> block.richText.plainText.length
                is BookBlock.HeadingBlock -> block.richText.plainText.length
                else -> 0
            }
            // Si ajouter ce bloc dépasserait la limite et le batch
            // courant n'est pas vide, on le ferme.
            if (currentChars > 0 && currentChars + blockLen > maxChars) {
                batches.add(currentBatch.toList())
                currentBatch = mutableListOf()
                currentChars = 0
            }
            currentBatch.add(block)
            currentChars += blockLen
        }
        if (currentBatch.isNotEmpty()) {
            batches.add(currentBatch.toList())
        }
        return batches
    }

    /**
     * Construit un [AnnotatedString] et les offsets de phrase locaux
     * pour un lot de blocs.
     *
     * @return Pair(AnnotatedString, List<Int> des offsets de début de phrase)
     */
    private fun buildBatchAnnotatedString(
        blocks: List<BookBlock>,
    ): Pair<AnnotatedString, List<Int>> {
        val sentenceStartOffsets = mutableListOf<Int>()
        val annotatedString = buildAnnotatedString {
            for (block in blocks) {
                when (block) {
                    is BookBlock.ParagraphBlock -> {
                        val plainText = block.richText.plainText
                        sentenceStartOffsets.add(length)
                        // Appliquer les spans inline
                        val spans = block.richText.spans
                        var lastEnd = 0
                        for (span in spans) {
                            if (span.start > lastEnd) {
                                append(plainText.substring(lastEnd, span.start))
                            }
                            withStyle(BookBlockStyleMapper.spanStyleFor(span.styles)) {
                                append(plainText.substring(span.start, span.end))
                            }
                            lastEnd = span.end
                        }
                        if (lastEnd < plainText.length) {
                            append(plainText.substring(lastEnd))
                        }
                    }
                    is BookBlock.HeadingBlock -> {
                        val plainText = block.richText.plainText
                        sentenceStartOffsets.add(length)
                        val spans = block.richText.spans
                        var lastEnd = 0
                        for (span in spans) {
                            if (span.start > lastEnd) {
                                append(plainText.substring(lastEnd, span.start))
                            }
                            withStyle(BookBlockStyleMapper.spanStyleFor(span.styles)) {
                                append(plainText.substring(span.start, span.end))
                            }
                            lastEnd = span.end
                        }
                        if (lastEnd < plainText.length) {
                            append(plainText.substring(lastEnd))
                        }
                    }
                    else -> { /* ImageBlock, SeparatorBlock ignorés */ }
                }
            }
        }
        return annotatedString to sentenceStartOffsets
    }

    private companion object {
        const val DEFAULT_PREFIX_CHAR_BUDGET = 6000
        /** Taille max d'un lot en caractères (Plan v3, Palier 3.5). */
        const val MAX_BATCH_CHARS = 10_000
    }
}
