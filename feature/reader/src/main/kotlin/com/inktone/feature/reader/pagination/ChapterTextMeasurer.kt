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
import com.inktone.domain.model.Sentence
import com.inktone.feature.reader.rendering.BookBlockStyleMapper

/**
 * Résultat de la mesure d'un chapitre — entier ou d'un préfixe borné
 * (voir `measureFirstPage`). `annotatedString` est réutilisé tel quel
 * par le rendu du mode pagé (3a.2) — tranché par offset selon la page,
 * jamais reconstruit par page ni par mot prononcé (voir l'exigence de
 * coût de recomposition de 3a.2). `sentenceStartOffsets[i]` est
 * l'offset, dans ce même `annotatedString`, où commence la phrase
 * d'index global `i` (même indexation que `chapter.sentences`) — sa
 * taille indique combien de phrases, depuis le début du chapitre, sont
 * couvertes par cette mesure : moins que le total pour un préfixe, tout
 * le chapitre sinon.
 */
data class ChapterMeasurement(
    val annotatedString: AnnotatedString,
    val lines: List<LineGeometry>,
    val sentenceStartOffsets: List<Int>,
)

/**
 * Indexe les phrases par [Sentence.blockIndex], en un seul passage (O(n)).
 * Partagé entre le mesureur ([ChapterTextMeasurer.measureRich]) et le
 * compteur d'offsets mesurables ([measurableOffsetCount]) : filtrer
 * `sentences.filter { it.blockIndex == X }` une fois par bloc rendait la
 * mesure quadratique sur les longs chapitres (O(blocs × phrases)).
 */
internal fun sentencesByBlockIndex(sentences: List<Sentence>): Map<Int, List<Sentence>> =
    sentences.groupBy { it.blockIndex }

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
     * 1. Borner les [BookBlock.ParagraphBlock]/[BookBlock.HeadingBlock] au
     *    budget total [maxChars] (préfixe, [measureFirstPage] — jamais
     *    borné pour [measure], budget = `Int.MAX_VALUE`).
     * 2. Découper le résultat en lots de [MAX_BATCH_CHARS] caractères max
     *    — TOUJOURS appliqué, indépendamment de [maxChars] : c'est la
     *    seule protection contre le dépassement de texture GPU sur un
     *    chapitre long, y compris pour [measure] (préfixe illimité).
     *    Frontières de lot TOUJOURS entre deux blocs (jamais au milieu).
     * 3. Mesurer chaque lot indépendamment via [measureBuilt].
     * 4. Accumuler les [LineGeometry] avec `top`/`bottom` ajustés
     *    (décalage vertical cumulatif).
     * 5. Accumuler les `sentenceStartOffsets` dans l'espace global
     *    (somme des longueurs de tous les lots précédents).
     */
    private fun measureRich(
        chapter: Chapter,
        baseStyle: TextStyle,
        maxWidthPx: Int,
        maxChars: Int,
    ): ChapterMeasurement {
        val content = chapter.content as? ChapterContent.Rich
            ?: error("ChapterTextMeasurer.measureRich appelé sur un chapitre sans ChapterContent.Rich (${chapter.href})")
        // Index des phrases par bloc, construit UNE FOIS (O(n)) puis partagé
        // entre tous les lots : le filtrage par bloc répété dans
        // `buildBatchAnnotatedString` était quadratique (voir §4.5 de
        // l'audit de réactivité).
        val sentencesByBlock = sentencesByBlockIndex(chapter.sentences)
        // IndexedValue : conserve l'index ORIGINAL dans `content.blocks` — le
        // même référentiel que Sentence.blockIndex — pour retrouver les
        // phrases de chaque bloc plus bas malgré le filtrage.
        val textBlocks = content.blocks.withIndex().filter {
            it.value is BookBlock.ParagraphBlock || it.value is BookBlock.HeadingBlock
        }
        if (textBlocks.isEmpty()) {
            return ChapterMeasurement(AnnotatedString(""), emptyList(), emptyList())
        }

        // 1. Borner au budget total demandé — [maxChars] est un budget de
        //    PRÉFIXE (measureFirstPage), pas une taille de lot. Avant ce
        //    correctif, [buildBatches] recevait directement [maxChars] comme
        //    seuil de lot : measure() (maxChars = Int.MAX_VALUE) produisait
        //    alors un unique lot contenant TOUT le chapitre (aucun
        //    découpage réel), et measureFirstPage() mesurait la totalité du
        //    chapitre au lieu de s'arrêter au préfixe — les deux à l'inverse
        //    de l'intention du Palier 3.5.
        val boundedBlocks = mutableListOf<IndexedValue<BookBlock>>()
        var runningChars = 0
        for (indexed in textBlocks) {
            boundedBlocks.add(indexed)
            runningChars += textLengthOf(indexed.value)
            if (runningChars >= maxChars) break
        }

        // 2. Découper le préfixe borné en lots de taille structurelle fixe
        //    (MAX_BATCH_CHARS) — toujours appliqué, indépendamment de
        //    [maxChars], seule protection réelle contre le dépassement de
        //    texture GPU sur un chapitre long.
        val batches = buildBatches(boundedBlocks, MAX_BATCH_CHARS)
        if (batches.isEmpty()) {
            return ChapterMeasurement(AnnotatedString(""), emptyList(), emptyList())
        }

        // 3. Mesurer chaque lot et accumuler
        val allLines = mutableListOf<LineGeometry>()
        val allSentenceOffsets = mutableListOf<Int>()
        val batchAnnotatedStrings = mutableListOf<AnnotatedString>()
        var cumulativeTop = 0f
        var globalOffset = 0

        batches.forEachIndexed { batchIndex, batch ->
            val (annotatedString, localOffsets) = buildBatchAnnotatedString(
                blocks = batch,
                sentencesByBlock = sentencesByBlock,
                // Un "\n" doit séparer TOUT couple de blocs de texte
                // consécutifs, y compris à une frontière de lot — sinon
                // l'espace d'offsets global dérive de 1 caractère par
                // frontière face à celui de JsoupChapterParser (même
                // convention de séparateur, voir Chapter.sentences), et
                // les offsets TTS/sélection calculés par ReaderScreen en
                // mode SCROLL (qui utilise ce même Chapter.sentences)
                // désynchronisent du rendu pagé pour tout chapitre
                // dépassant MAX_BATCH_CHARS.
                leadingSeparator = batchIndex > 0,
            )
            batchAnnotatedStrings.add(annotatedString)
            val measurement = measureBuilt(annotatedString, localOffsets, baseStyle, maxWidthPx)

            // Ajuster les lignes dans l'espace global : `top`/`bottom` sont
            // décalés de la hauteur cumulée des lots précédents, ET
            // `startOffset`/`endOffset` du nombre de caractères déjà
            // consommés (`globalOffset`) — SANS ce second décalage, les
            // offsets de lignes du lot 2+ retombaient à 0 (espace LOCAL du
            // lot) alors que `annotatedString` et les offsets de phrases
            // sont, eux, en espace GLOBAL : `computePageLineRanges` pouvait
            // alors produire des `pageOffsetRange` vides (start > end) sur
            // toute page chevauchant une frontière de lot — la page blanche
            // observée sur appareil. Les deux décalages vont de pair.
            for (line in measurement.lines) {
                allLines.add(
                    line.copy(
                        top = line.top + cumulativeTop,
                        bottom = line.bottom + cumulativeTop,
                        startOffset = line.startOffset + globalOffset,
                        endOffset = line.endOffset + globalOffset,
                    ),
                )
            }

            // Ajuster les offsets de phrase dans l'espace global
            for (offset in measurement.sentenceStartOffsets) {
                allSentenceOffsets.add(offset + globalOffset)
            }

            cumulativeTop += measurement.lines.lastOrNull()?.bottom ?: 0f
            globalOffset += annotatedString.text.length
        }

        // Bug réel trouvé sur appareil (page blanche après ~3 swipes en
        // mode paginé) : l'ancien code ne stockait que le PREMIER lot
        // (`firstBatchAnnotated`) en croyant qu'il « suffisait comme
        // référence pour les offsets ». Faux : `buildPageAnnotatedString`
        // (PagedChapterContent) tranche le TEXTE de la page à partir de cet
        // `AnnotatedString` — au-delà des MAX_BATCH_CHARS premiers
        // caractères, la tranche tombait hors du texte stocké et rendait
        // une page vide. Le rendu doit disposer du texte INTÉGRAL : on
        // concatène donc tous les lots. Chaque lot porte déjà son
        // séparateur "\n" de tête (`leadingSeparator`), et `globalOffset`
        // en tient compte — la concaténation directe préserve exactement
        // l'espace d'offsets global de `allLines`/`allSentenceOffsets`.
        // La mesure, elle, reste bien par lots (protection texture GPU) :
        // concaténer des AnnotatedString ne déclenche aucune mesure.
        val fullAnnotatedString = when (batchAnnotatedStrings.size) {
            0 -> AnnotatedString("")
            1 -> batchAnnotatedStrings[0]
            else -> buildAnnotatedString {
                batchAnnotatedStrings.forEach { append(it) }
            }
        }
        return ChapterMeasurement(fullAnnotatedString, allLines, allSentenceOffsets)
    }

    /**
     * Découpe les blocs de texte en lots de [maxChars] caractères max.
     * Les frontières tombent toujours entre deux blocs.
     */
    private fun buildBatches(
        textBlocks: List<IndexedValue<BookBlock>>,
        maxChars: Int,
    ): List<List<IndexedValue<BookBlock>>> {
        val batches = mutableListOf<List<IndexedValue<BookBlock>>>()
        var currentBatch = mutableListOf<IndexedValue<BookBlock>>()
        var currentChars = 0

        for (indexed in textBlocks) {
            val blockLen = textLengthOf(indexed.value)
            // Si ajouter ce bloc dépasserait la limite et le batch
            // courant n'est pas vide, on le ferme.
            if (currentChars > 0 && currentChars + blockLen > maxChars) {
                batches.add(currentBatch.toList())
                currentBatch = mutableListOf()
                currentChars = 0
            }
            currentBatch.add(indexed)
            currentChars += blockLen
        }
        if (currentBatch.isNotEmpty()) {
            batches.add(currentBatch.toList())
        }
        return batches
    }

    private fun textLengthOf(block: BookBlock): Int = when (block) {
        is BookBlock.ParagraphBlock -> block.richText.plainText.length
        is BookBlock.HeadingBlock -> block.richText.plainText.length
        else -> 0
    }

    /**
     * Construit un [AnnotatedString] et les offsets de DÉBUT DE PHRASE
     * (pas de bloc) locaux pour un lot de blocs, à partir de [sentences]
     * (`chapter.sentences`, filtrées par [Sentence.blockIndex] pour
     * retrouver celles de chaque bloc). Un séparateur (retour à la ligne)
     * est inséré entre deux blocs consécutifs du même lot, pour ne pas
     * fusionner visuellement deux paragraphes.
     *
     * Repli sur UN offset = le début du bloc lui-même quand aucune
     * [Sentence] n'a de [Sentence.blockIndex] pointant vers ce bloc —
     * jamais un bloc sans aucune entrée. Couvre le cas réel où plusieurs
     * phrases partagent un bloc (le cas voulu, précis), et le cas où
     * [sentences] ne porte pas de `blockIndex` valide (fixtures qui ne le
     * renseignent pas) sans jamais désynchroniser `sentenceStartOffsets`
     * du nombre de blocs mesurés.
     *
     * @return Pair(AnnotatedString, List<Int> des offsets de début de phrase)
     */
    private fun buildBatchAnnotatedString(
        blocks: List<IndexedValue<BookBlock>>,
        sentencesByBlock: Map<Int, List<Sentence>>,
        leadingSeparator: Boolean,
    ): Pair<AnnotatedString, List<Int>> {
        val sentenceStartOffsets = mutableListOf<Int>()
        val annotatedString = buildAnnotatedString {
            blocks.forEachIndexed { position, (originalIndex, block) ->
                val richText = when (block) {
                    is BookBlock.ParagraphBlock -> block.richText
                    is BookBlock.HeadingBlock -> block.richText
                    else -> null
                } ?: return@forEachIndexed

                if (position > 0 || leadingSeparator) append('\n')
                val blockStartInBatch = length
                val blockGlobalStart = block.globalOffsetRange?.first ?: 0
                val blockSentences = sentencesByBlock[originalIndex].orEmpty()
                if (blockSentences.isNotEmpty()) {
                    blockSentences.forEach { sentence ->
                        sentenceStartOffsets.add(blockStartInBatch + (sentence.startOffset - blockGlobalStart))
                    }
                } else {
                    sentenceStartOffsets.add(blockStartInBatch)
                }

                val plainText = richText.plainText
                val spans = richText.spans
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
        }
        return annotatedString to sentenceStartOffsets
    }

    private companion object {
        const val DEFAULT_PREFIX_CHAR_BUDGET = 6000
        /** Taille max d'un lot en caractères (Plan v3, Palier 3.5). */
        const val MAX_BATCH_CHARS = 10_000
    }
}
