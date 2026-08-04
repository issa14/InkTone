package com.inktone.feature.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.Sentence
import com.inktone.feature.reader.pagination.ChapterPaginationState

/**
 * 3a.2/3b.1 — Contenu paginé par swipe horizontal, rendu depuis le
 * moteur de pagination réelle (3a.1). Chaque page est **un seul bloc de
 * texte** tranché depuis l'`AnnotatedString` du chapitre — plus le
 * `FlowRow` de composables `SentenceText` séparés de l'ancienne
 * implémentation, qui empêchait toute mesure fidèle et recherchait
 * chaque phrase par `indexOf` (O(n²) par page).
 *
 * **Consommateur, pas producteur, de la pagination (3b.1)** : la mesure
 * et le `VirtualPaginationEngine` vivent désormais au-dessus du choix de
 * mode de rendu (`ChapterPaginationState`, construit par
 * `rememberChapterPaginationState` sous `ReaderScreen`), pour que la
 * ligne de statut (3b.4, tous modes) et ce rendu partagent le même
 * calcul. Ce composable ne mesure plus lui-même et ne connaît plus le
 * viewport — sa taille lui vient du `Box` parent, déjà mesuré au même
 * endroit pour les deux modes.
 *
 * **Isolation du surlignage (contrainte structurante de 3a.2)** : le mot
 * en cours et la sélection sont lus **au plus tard**, via `State` capturé
 * en phase de dessin (`drawWithContent`), jamais passés en paramètre du
 * bloc de page — un changement de mot prononcé ne déclenche donc qu'un
 * redessin, jamais une remesure ni un replacement de la page.
 *
 * **Phrase à cheval sur deux pages** (design validé avant implémentation,
 * voir revue du lot 3a) : une phrase appartient entièrement, pour
 * l'indexation (`sentenceRangeOf`/`pageIndexAt`), à la page où elle
 * commence — même si son rendu déborde visuellement sur la suivante.
 * Le pager suit alors l'offset absolu du mot en cours (pas seulement
 * l'index de phrase) pour basculer automatiquement de page quand ce mot
 * est physiquement rendu sur la page suivante.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagedChapterContent(
    chapter: Chapter?,
    pagination: ChapterPaginationState,
    currentSentenceIndex: Int,
    highlightedWordRange: IntRange?,
    selectedRange: IntRange?,
    annotations: List<Annotation>,
    currentChapterIndex: Int,
    textColor: Color,
    isReadingRulerEnabled: Boolean,
    onSentenceLongClick: (Int) -> Unit,
    onSentenceClick: (Int) -> Unit,
    onNextChapter: () -> Unit,
    onCurrentLineY: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderTextStyle = remember(pagination.baseTextStyle, textColor) {
        pagination.baseTextStyle.copy(color = textColor)
    }

    val sentences = chapter?.paragraphs?.flatMap { it.sentences } ?: emptyList()

    val pageCount = if (chapter != null) pagination.pageCount(chapter.index) else 1

    // Page fantôme au-delà de la dernière (conservée telle quelle, 3a.1 —
    // ne pas refactoriser : c'est le correctif d'un bug réel déjà trouvé
    // à l'audit, signal non ambigu d'un swipe volontaire au-delà du
    // chapitre).
    val pagerState = rememberPagerState(pageCount = { pageCount + 1 })
    LaunchedEffect(pagerState.currentPage, pageCount) {
        if (pagerState.currentPage >= pageCount) {
            onNextChapter()
        }
    }

    // Ancrage de position (3a.1) : capturer currentSentenceIndex et
    // repositionner via pageIndexAt à chaque recalcul de pagination
    // (rotation, taille de police...) — jamais un index de page persisté.
    LaunchedEffect(chapter?.index, pagination.measurement, currentSentenceIndex) {
        if (chapter != null && pagination.measurement != null && pageCount > 0) {
            val targetPage = pagination.pageIndexAt(chapter.index, currentSentenceIndex)
            if (pagerState.currentPage != targetPage) {
                pagerState.scrollToPage(targetPage)
            }
        }
    }

    // `pagination.measurement` (State) ne bénéficie pas du smart cast
    // Kotlin après un null-check — capturer une copie locale non
    // nullable partout où c'est nécessaire.
    val currentMeasurement = pagination.measurement

    val absoluteHighlightedRange = if (
        chapter != null && currentMeasurement != null && highlightedWordRange != null &&
        currentSentenceIndex in currentMeasurement.sentenceStartOffsets.indices
    ) {
        val sentenceStart = currentMeasurement.sentenceStartOffsets[currentSentenceIndex]
        (sentenceStart + highlightedWordRange.first)..(sentenceStart + highlightedWordRange.last)
    } else {
        null
    }

    // Design validé (revue du lot 3a) : une phrase à cheval sur deux
    // pages reste indexée sur la page où elle commence (pageIndexAt),
    // mais le mot effectivement prononcé peut être rendu sur la page
    // suivante. On bascule alors le pager sur l'offset absolu du mot,
    // pas sur l'index de phrase — sinon le surlignage deviendrait
    // invisible (rendu sur une page non affichée) sans que rien ne le
    // signale.
    LaunchedEffect(chapter?.index, absoluteHighlightedRange) {
        if (chapter != null && absoluteHighlightedRange != null) {
            val wordPage = pagination.pageIndexAtOffset(chapter.index, absoluteHighlightedRange.first)
            if (wordPage >= 0 && wordPage != pagerState.currentPage) {
                pagerState.animateScrollToPage(wordPage)
            }
        }
    }

    // Lus en phase de dessin par PageBlock, jamais en paramètre de
    // composable : un mot prononcé ne doit invalider que le dessin.
    val highlightedRangeState = rememberUpdatedState(absoluteHighlightedRange)

    // measurement.sentenceStartOffsets peut être plus court que sentences
    // pendant la mesure en deux temps (3a.3, préfixe pas encore élargi) -
    // borner sur ses propres indices, jamais sur ceux de sentences.
    val absoluteSelectedRange = if (
        chapter != null && currentMeasurement != null && selectedRange != null &&
        currentMeasurement.sentenceStartOffsets.isNotEmpty()
    ) {
        val startSentence = selectedRange.first.coerceIn(currentMeasurement.sentenceStartOffsets.indices)
        val endSentence = selectedRange.last.coerceIn(currentMeasurement.sentenceStartOffsets.indices)
        val start = currentMeasurement.sentenceStartOffsets[startSentence]
        val end = currentMeasurement.sentenceStartOffsets[endSentence] + sentences[endSentence].text.length - 1
        start..end
    } else {
        null
    }
    val selectedRangeState = rememberUpdatedState(absoluteSelectedRange)

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
    ) { pageIndex ->
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (chapter != null && currentMeasurement != null && pageIndex < pageCount) {
                val pageOffsetRange = pagination.pageOffsetRange(chapter.index, pageIndex)
                val pageSentenceRange = pagination.sentenceRangeOf(chapter.index, pageIndex)
                if (!pageOffsetRange.isEmpty()) {
                    val pageText = remember(currentMeasurement, pageOffsetRange, pageSentenceRange, annotations) {
                        buildPageAnnotatedString(
                            full = currentMeasurement.annotatedString,
                            pageOffsetRange = pageOffsetRange,
                            sentences = sentences,
                            sentenceStartOffsets = currentMeasurement.sentenceStartOffsets,
                            pageSentenceRange = pageSentenceRange,
                            chapterIndex = currentChapterIndex,
                            annotations = annotations,
                        )
                    }
                    PageBlock(
                        pageText = pageText,
                        pageOffsetRange = pageOffsetRange,
                        textStyle = renderTextStyle,
                        highlightedRange = highlightedRangeState,
                        selectedRange = selectedRangeState,
                        isDisplayedPage = pageIndex == pagerState.currentPage,
                        isReadingRulerEnabled = isReadingRulerEnabled,
                        onCurrentLineY = onCurrentLineY,
                        onOffsetLongPress = { absoluteOffset ->
                            onSentenceLongClick(sentenceIndexForOffset(currentMeasurement.sentenceStartOffsets, absoluteOffset))
                        },
                        onOffsetTap = { absoluteOffset ->
                            onSentenceClick(sentenceIndexForOffset(currentMeasurement.sentenceStartOffsets, absoluteOffset))
                        },
                    )
                }
            }
        }
    }
}

/**
 * Un bloc de texte par page — plus un `Text` par phrase. `pageText`
 * porte déjà les couleurs d'annotation existantes (posées une fois à la
 * construction, elles ne changent pas à cadence TTS). Le surlignage
 * mot-à-mot et la sélection en cours, eux, sont lus en phase de dessin
 * (`drawWithContent`) pour ne jamais invalider mesure ni placement.
 */
@Composable
private fun PageBlock(
    pageText: AnnotatedString,
    pageOffsetRange: IntRange,
    textStyle: TextStyle,
    highlightedRange: State<IntRange?>,
    selectedRange: State<IntRange?>,
    isDisplayedPage: Boolean,
    isReadingRulerEnabled: Boolean,
    onCurrentLineY: (Dp) -> Unit,
    onOffsetLongPress: (Int) -> Unit,
    onOffsetTap: (Int) -> Unit,
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current

    Text(
        text = pageText,
        style = textStyle,
        onTextLayout = { layout ->
            textLayoutResult = layout
            if (isDisplayedPage && isReadingRulerEnabled) {
                val absRange = highlightedRange.value
                if (absRange != null) {
                    val local = absRange.first - pageOffsetRange.first
                    if (local in 0 until layout.layoutInput.text.length) {
                        val line = layout.getLineForOffset(local)
                        onCurrentLineY(with(density) { layout.getLineTop(line).toDp() })
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(pageOffsetRange) {
                detectTapGestures(
                    onLongPress = { position ->
                        textLayoutResult?.let { layout ->
                            onOffsetLongPress(pageOffsetRange.first + layout.getOffsetForPosition(position))
                        }
                    },
                    onTap = { position ->
                        textLayoutResult?.let { layout ->
                            onOffsetTap(pageOffsetRange.first + layout.getOffsetForPosition(position))
                        }
                    },
                )
            }
            .drawWithContent {
                textLayoutResult?.let { layout ->
                    selectedRange.value?.let { absolute ->
                        drawAbsoluteRangeHighlight(layout, pageOffsetRange, absolute, SelectionHighlightColor)
                    }
                    highlightedRange.value?.let { absolute ->
                        drawAbsoluteRangeHighlight(layout, pageOffsetRange, absolute, WordHighlightColor)
                    }
                }
                drawContent()
            },
    )
}

private fun DrawScope.drawAbsoluteRangeHighlight(
    layout: TextLayoutResult,
    pageOffsetRange: IntRange,
    absoluteRange: IntRange,
    color: Color,
) {
    val textLength = layout.layoutInput.text.length
    val localStart = (absoluteRange.first - pageOffsetRange.first).coerceAtLeast(0)
    val localEndExclusive = (absoluteRange.last + 1 - pageOffsetRange.first).coerceAtMost(textLength)
    if (localStart >= localEndExclusive) return
    drawPath(layout.getPathForRange(localStart, localEndExclusive), color = color)
}

private val WordHighlightColor = Color(0xFFFFEB3B)
private val SelectionHighlightColor = Color(0x664FC3F7)

/** Recherche linéaire volontaire : appelée uniquement sur tap/appui long, jamais par recomposition ni par mot prononcé — pas le O(n²) par page que corrige 3a.2. */
private fun sentenceIndexForOffset(sentenceStartOffsets: List<Int>, absoluteOffset: Int): Int {
    var result = 0
    for (i in sentenceStartOffsets.indices) {
        if (sentenceStartOffsets[i] <= absoluteOffset) result = i else break
    }
    return result
}

private fun buildPageAnnotatedString(
    full: AnnotatedString,
    pageOffsetRange: IntRange,
    sentences: List<Sentence>,
    sentenceStartOffsets: List<Int>,
    pageSentenceRange: IntRange,
    chapterIndex: Int,
    annotations: List<Annotation>,
): AnnotatedString {
    val endExclusive = (pageOffsetRange.last + 1).coerceAtMost(full.length)
    val base = full.subSequence(pageOffsetRange.first, endExclusive)
    if (pageSentenceRange.isEmpty()) return base

    return buildAnnotatedString {
        append(base)
        for (sentenceIndex in pageSentenceRange) {
            if (sentenceIndex !in sentences.indices) continue
            val color = annotationColorFor(chapterIndex, sentences[sentenceIndex], annotations) ?: continue
            val localStart = (sentenceStartOffsets[sentenceIndex] - pageOffsetRange.first).coerceAtLeast(0)
            val localEndExclusive =
                (sentenceStartOffsets[sentenceIndex] + sentences[sentenceIndex].text.length - pageOffsetRange.first)
                    .coerceAtMost(base.length)
            if (localStart < localEndExclusive) {
                addStyle(SpanStyle(background = color.toComposeColor()), localStart, localEndExclusive)
            }
        }
    }
}

private fun annotationColorFor(chapterIndex: Int, sentence: Sentence, annotations: List<Annotation>): AnnotationColor? =
    annotations.firstOrNull { annotation ->
        annotation.startLocator.chapterIndex == chapterIndex &&
            sentence.startOffset < annotation.endLocator.charOffset &&
            sentence.endOffset > annotation.startLocator.charOffset
    }?.color
