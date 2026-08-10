package com.inktone.feature.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
    onPageChanged: (Int) -> Unit = {},
    onManualPageChange: (sentenceIndex: Int) -> Unit = {},
    onSelectionBoundsInWindow: (Rect?) -> Unit = {},
    // Palier 3f.1 — sélection libre au mot, offsets de caractère absolus
    // dans le chapitre (pas des index de Sentence, voir ReaderUiState).
    freeSelectedRange: IntRange? = null,
    onFreeSelectionChanged: (anchorOffset: Int, focusOffset: Int) -> Unit = { _, _ -> },
    onFreeSelectionBoundsInWindow: (Rect?) -> Unit = {},
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

    // 3c.1bis — bug réel trouvé sur appareil pendant la vérification du
    // lot 3c : le compteur de PAGE suit déjà le swipe manuel
    // (onPageChanged ci-dessous, corrigé au lot 3b), mais le POURCENTAGE
    // de progression (ReaderUiState.bookProgression) dérive de
    // currentSentenceIndex, jamais mis à jour par un swipe — seuls le TTS
    // ou une navigation explicite l'avancent. Même antipattern que celui
    // corrigé en mode SCROLL (3c.1), pour le mode PAGED. Même garde
    // anti-boucle : un drapeau posé explicitement autour des deux SEULS
    // appels programmatiques au pager (restauration de position ci-dessous,
    // suivi du mot prononcé plus bas), jamais isScrollInProgress qui ne
    // distingue pas swipe manuel et scrollToPage/animateScrollToPage.
    var isProgrammaticPageChange by remember { mutableStateOf(false) }

    // Bug réel trouvé sur appareil (clignotement frénétique après 2-3
    // swipes) : `onManualPageChange` pousse `sentenceRangeOf(page).first`
    // vers `currentSentenceIndex`, qui redéclenche aussitôt l'effet
    // d'ancrage ci-dessous (`pageIndexAt`, keyé sur currentSentenceIndex).
    // Si `pageIndexAt(sentenceRangeOf(page).first)` ne redonne pas
    // exactement `page` (une phrase à cheval sur deux pages, notamment),
    // l'ancrage rescrolle vers une page légèrement différente, ce qui
    // redéclenche `onManualPageChange` sur CETTE nouvelle page — boucle.
    // Mémorise le dernier index émis PAR ce composable lui-même : quand
    // `currentSentenceIndex` revient exactement à cette valeur, c'est cet
    // écho, pas une navigation externe (TTS, signet, chapitre) — l'effet
    // d'ancrage ne doit alors PAS re-scroller, il doit faire confiance au
    // pager plutôt que de le contredire.
    var lastManuallyEmittedSentenceIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(pagerState.currentPage, pageCount) {
        if (pagerState.currentPage >= pageCount) {
            onNextChapter()
        } else {
            // Remonte la page réellement affichée — swipe manuel inclus,
            // pas seulement la progression pilotée par le TTS. Bug réel
            // trouvé sur appareil (lot 3b) : le compteur de la ligne de
            // statut restait figé à 1 pendant un swipe manuel, puisque
            // rien ne faisait remonter la position du pager avant ceci.
            onPageChanged(pagerState.currentPage)

            if (!isProgrammaticPageChange && chapter != null) {
                val sentenceRange = pagination.sentenceRangeOf(chapter.index, pagerState.currentPage)
                if (!sentenceRange.isEmpty()) {
                    lastManuallyEmittedSentenceIndex = sentenceRange.first
                    onManualPageChange(sentenceRange.first)
                }
            }
        }
    }

    // Ancrage de position (3a.1) : capturer currentSentenceIndex et
    // repositionner via pageIndexAt à chaque recalcul de pagination
    // (rotation, taille de police...) — jamais un index de page persisté.
    //
    // Régression connue, documentée, non corrigée (voir
    // docs/execution/NOTE_REGRESSION_CLIGNOTEMENT_PAGE_HUD.md) : cet
    // effet se relance à CHAQUE écriture intermédiaire de
    // pagination.measurement pendant une mesure progressive (pas
    // seulement la finale) — si currentSentenceIndex est profond dans le
    // chapitre, pageIndexAt calculé contre une mesure encore partielle
    // peut être transitoirement faux, causant un saut de page visible.
    // Le HUD (ReaderScreen.readingAreaSize) redéclenche ce genre de
    // remesure à chaque bascule visible/masqué.
    LaunchedEffect(chapter?.index, pagination.measurement, currentSentenceIndex) {
        if (currentSentenceIndex == lastManuallyEmittedSentenceIndex) {
            // Écho de notre propre swipe (voir commentaire ci-dessus) :
            // le pager est déjà à la bonne page, ne pas le contredire.
            lastManuallyEmittedSentenceIndex = null
            return@LaunchedEffect
        }
        if (chapter != null && pagination.measurement != null && pageCount > 0) {
            val targetPage = pagination.pageIndexAt(chapter.index, currentSentenceIndex)
            if (pagerState.currentPage != targetPage) {
                isProgrammaticPageChange = true
                try {
                    pagerState.scrollToPage(targetPage)
                } finally {
                    isProgrammaticPageChange = false
                }
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
                isProgrammaticPageChange = true
                try {
                    pagerState.animateScrollToPage(wordPage)
                } finally {
                    isProgrammaticPageChange = false
                }
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

    // Palier 3f.1 — déjà en offsets de caractère absolus (calés sur des
    // bornes de mot par PageBlock), aucune conversion index-de-phrase à
    // faire ici contrairement à absoluteSelectedRange ci-dessus.
    val freeSelectedRangeState = rememberUpdatedState(freeSelectedRange)

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
                        onSelectionBoundsInWindow = onSelectionBoundsInWindow,
                        freeSelectedRange = freeSelectedRangeState,
                        onFreeSelectionChanged = onFreeSelectionChanged,
                        onFreeSelectionBoundsInWindow = onFreeSelectionBoundsInWindow,
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
@OptIn(ExperimentalFoundationApi::class)
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
    onSelectionBoundsInWindow: (Rect?) -> Unit,
    freeSelectedRange: State<IntRange?>,
    onFreeSelectionChanged: (anchorOffset: Int, focusOffset: Int) -> Unit,
    onFreeSelectionBoundsInWindow: (Rect?) -> Unit,
    onOffsetLongPress: (Int) -> Unit,
    onOffsetTap: (Int) -> Unit,
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current

    // Palier 3f.1 — plage (inclusive, absolue) du mot sous le doigt au
    // début du glissement, fixe pour tout le geste. À chaque étape du
    // glissement, la sélection émise est l'union de cette plage ancre et
    // de la plage du mot actuellement sous le doigt : correct pour un
    // glissement dans les deux sens sans avoir à suivre sa direction
    // explicitement (voir onDrag ci-dessous).
    var anchorWordRange by remember { mutableStateOf<IntRange?>(null) }

    // 3c.4 — position du popup de sélection alimentée par les
    // LayoutCoordinates réelles de la zone sélectionnée (contrainte
    // d'implémentation retenue, pas des coordonnées calculées à la main) :
    // convertit le rectangle LOCAL de la sélection (getPathForRange, même
    // mécanisme que le dessin du surlignage ci-dessous) en coordonnées
    // fenêtre via les coordonnées réelles du Text.
    //
    // Bug réel trouvé sur appareil (popup de sélection jamais affiché en
    // mode PAGED) : un `SideEffect` ne se ré-exécute QUE quand ce
    // composable (`PageBlock`) se recompose pour une AUTRE raison — lire
    // `selectedRange.value` à l'intérieur ne l'abonne à rien (c'est
    // volontaire pour `highlightedRange`, lu de la même façon en dessin
    // via `drawWithContent`, qui a sa propre observation réactive du
    // State — voir le commentaire de tête sur l'isolation du
    // surlignage : « ne déclenche donc qu'un redessin, jamais... un
    // replacement de la page »). Une sélection ne fait donc JAMAIS
    // recomposer `PageBlock`, le `SideEffect` ne se relançait donc
    // jamais après la sélection initiale. `snapshotFlow` observe l'État
    // directement, indépendamment de toute recomposition.
    LaunchedEffect(isDisplayedPage, pageOffsetRange) {
        if (!isDisplayedPage) {
            onSelectionBoundsInWindow(null)
            return@LaunchedEffect
        }
        snapshotFlow { selectedRange.value }.collect { absolute ->
            onSelectionBoundsInWindow(
                rangeBoundsInWindow(textLayoutResult, textCoordinates, pageOffsetRange, absolute),
            )
        }
    }

    // Palier 3f.1 — même mécanisme que ci-dessus, pour la sélection libre
    // au mot, source de bornes distincte pour le même popup (voir
    // SelectionActionPopup.kt).
    LaunchedEffect(isDisplayedPage, pageOffsetRange) {
        if (!isDisplayedPage) {
            onFreeSelectionBoundsInWindow(null)
            return@LaunchedEffect
        }
        snapshotFlow { freeSelectedRange.value }.collect { absolute ->
            onFreeSelectionBoundsInWindow(
                rangeBoundsInWindow(textLayoutResult, textCoordinates, pageOffsetRange, absolute),
            )
        }
    }

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
            .onGloballyPositioned { textCoordinates = it }
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
            // Palier 3f.1 — mécanisme validé par le prototype 3c.5 (30/30
            // sur mesure robuste, voir NOTE_3C5_PROTOTYPE_SELECTION_MOT.md) :
            // `pointerInput` SIBLING de celui du tap ci-dessus, avec
            // `detectDragGesturesAfterLongPress` + `change.consume()`, sans
            // mécanisme supplémentaire (ni `userScrollEnabled`, ni
            // participation à `NestedScrollConnection`) — suffisant pour
            // empêcher le `HorizontalPager` de tourner la page pendant le
            // glissement.
            .pointerInput(pageOffsetRange) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        val layout = textLayoutResult ?: return@detectDragGesturesAfterLongPress
                        val local = layout.getOffsetForPosition(position).coerceIn(0, layout.layoutInput.text.length)
                        val word = layout.getWordBoundary(local)
                        val range = (pageOffsetRange.first + word.start)..(pageOffsetRange.first + word.end - 1)
                        anchorWordRange = range
                        onFreeSelectionChanged(range.first, range.last)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val anchor = anchorWordRange ?: return@detectDragGesturesAfterLongPress
                        val layout = textLayoutResult ?: return@detectDragGesturesAfterLongPress
                        val local = layout.getOffsetForPosition(change.position).coerceIn(0, layout.layoutInput.text.length)
                        val word = layout.getWordBoundary(local)
                        val currentStart = pageOffsetRange.first + word.start
                        val currentEnd = pageOffsetRange.first + word.end - 1
                        onFreeSelectionChanged(minOf(anchor.first, currentStart), maxOf(anchor.last, currentEnd))
                    },
                    onDragEnd = { anchorWordRange = null },
                    onDragCancel = { anchorWordRange = null },
                )
            }
            .drawWithContent {
                textLayoutResult?.let { layout ->
                    selectedRange.value?.let { absolute ->
                        drawAbsoluteRangeHighlight(layout, pageOffsetRange, absolute, SelectionHighlightColor)
                    }
                    freeSelectedRange.value?.let { absolute ->
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

private fun rangeBoundsInWindow(
    layout: TextLayoutResult?,
    coords: LayoutCoordinates?,
    pageOffsetRange: IntRange,
    absolute: IntRange?,
): Rect? {
    if (layout == null || coords == null || absolute == null) return null
    val textLength = layout.layoutInput.text.length
    val localStart = (absolute.first - pageOffsetRange.first).coerceIn(0, textLength)
    val localEndExclusive = (absolute.last + 1 - pageOffsetRange.first).coerceIn(localStart, textLength)
    if (localStart >= localEndExclusive) return null
    val localBounds = layout.getPathForRange(localStart, localEndExclusive).getBounds()
    val topLeft = coords.localToWindow(localBounds.topLeft)
    val bottomRight = coords.localToWindow(localBounds.bottomRight)
    return Rect(topLeft, bottomRight)
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

internal fun buildPageAnnotatedString(
    full: AnnotatedString,
    pageOffsetRange: IntRange,
    sentences: List<Sentence>,
    sentenceStartOffsets: List<Int>,
    pageSentenceRange: IntRange,
    chapterIndex: Int,
    annotations: List<Annotation>,
): AnnotatedString {
    // Bug réel trouvé sur appareil (crash reproductible, plusieurs
    // occurrences en logcat) : `pageOffsetRange` (versionné via
    // `VirtualPaginationEngine`) et `full` (`currentMeasurement.annotatedString`,
    // un `State` distinct sur `ChapterPaginationState`) sont écrits
    // séparément par `rememberChapterPaginationState` pendant une mesure
    // progressive (changement de chapitre, de taille de police...). Une
    // recomposition transitoire peut donc lire un `pageOffsetRange` calculé
    // pour une mesure plus longue que le `full` déjà retombé sur la mesure
    // partielle du nouveau style — `pageOffsetRange.first` dépasse alors
    // `full.length` et `subSequence(start, end)` lève
    // IllegalArgumentException (start > end). Ce décalage d'une frame entre
    // les deux `State` est accepté par la conception (mesure asynchrone) :
    // borner aussi le DÉBUT, pas seulement la fin, rend cette frame
    // transitoire silencieuse (page vide un instant) plutôt qu'un crash —
    // la frame suivante, une fois les deux `State` synchronisés, affiche
    // le contenu correct.
    val startInclusive = pageOffsetRange.first.coerceIn(0, full.length)
    val endExclusive = (pageOffsetRange.last + 1).coerceIn(startInclusive, full.length)
    if (startInclusive >= endExclusive) return AnnotatedString("")
    val base = full.subSequence(startInclusive, endExclusive)
    if (pageSentenceRange.isEmpty()) return base

    return buildAnnotatedString {
        append(base)
        for (sentenceIndex in pageSentenceRange) {
            if (sentenceIndex !in sentences.indices) continue
            val color = annotationColorFor(chapterIndex, sentences[sentenceIndex], annotations) ?: continue
            val localStart = (sentenceStartOffsets[sentenceIndex] - startInclusive).coerceAtLeast(0)
            val localEndExclusive =
                (sentenceStartOffsets[sentenceIndex] + sentences[sentenceIndex].text.length - startInclusive)
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
