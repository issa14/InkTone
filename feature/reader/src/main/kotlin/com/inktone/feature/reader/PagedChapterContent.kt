package com.inktone.feature.reader

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import com.inktone.feature.reader.transition.ChapterTransitionConnection
import com.inktone.feature.reader.transition.ChapterTransitionDirection
import com.inktone.feature.reader.transition.ChapterTransitionIndicator
import com.inktone.feature.reader.transition.ChapterTransitionMath
import com.inktone.feature.reader.transition.ChapterTransitionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import com.inktone.core.designsystem.rememberAppHaptics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Chapter
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
    annotations: List<Annotation>,
    currentChapterIndex: Int,
    chapterCount: Int = currentChapterIndex + 1,
    textColor: Color,
    isReadingRulerEnabled: Boolean,
    /**
     * P4 — marge de page. Doit être la MÊME valeur que le `paddingPx` remis à
     * `rememberChapterPaginationState` : mesurer une page plus large que celle
     * dessinée ferait déborder la dernière ligne hors de l'écran. Défaut égal
     * à l'ancienne valeur en dur, pour que tout appelant non migré rende comme
     * avant.
     */
    contentPadding: Dp = 16.dp,
    onClick: () -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit = {},
    hasPreviousChapter: Boolean = false,
    hasNextChapter: Boolean = false,
    reduceMotion: Boolean = false,
    surfaceColor: Color,
    isChapterReady: (Int) -> Boolean = { true },
    onCurrentLineY: (Dp) -> Unit,
    onPageChanged: (Int) -> Unit = {},
    onManualPageChange: (sentenceIndex: Int) -> Unit = {},
    // Sélection libre au mot, offsets de caractère absolus dans le
    // chapitre.
    freeSelectedRange: IntRange? = null,
    onFreeSelectionChanged: (anchorOffset: Int, focusOffset: Int) -> Unit = { _, _ -> },
    onFreeSelectionCleared: () -> Unit = {},
    // `ownerKey` — offset absolu de début de la PAGE émettrice. Plusieurs
    // pages sont montées simultanément (`beyondViewportPageCount = 1`) et
    // écrivent donc dans le même emplacement de bornes chez le parent :
    // sans cette identité, le `hide()` tardif d'une page pourrait effacer
    // le popup qu'une AUTRE page vient d'ouvrir. Arbitrage :
    // `resolveSelectionPopupBounds` (ReaderScreen).
    onFreeSelectionBoundsInWindow: (ownerKey: Int, bounds: Rect?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val renderTextStyle = remember(pagination.baseTextStyle, textColor) {
        pagination.baseTextStyle.copy(color = textColor)
    }

    val pageCount = if (chapter != null) pagination.pageCount(chapter.index) else 1

    // Le swipe au-delà de la dernière page est désormais géré par la
    // transition à résistance spatiale (ChapterTransitionConnection) :
    // plus de page fantôme.
    val pagerState = rememberPagerState(pageCount = { pageCount })

    // P5 — échelle haptique de l'app (core:designsystem), jamais une
    // vibration fabriquée sur place.
    val haptics = rememberAppHaptics()

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

    // Bug réel trouvé sur appareil (chapitre sauté après un swipe en fin
    // de chapitre, ex. "Chapitre 2" → "Chapitre 4") : au swipe au-delà de
    // la dernière page (page fantôme ci-dessus), onNextChapter() fait
    // avancer le chapitre côté parent, mais rien ne remettait
    // `pagerState` à la page 0 — contrairement au mode SCROLL
    // (ReaderScreen, `scrollState.scrollToItem(0)` sur changement de
    // chapitre). Une première version de ce correctif utilisait un
    // `LaunchedEffect(chapter?.index)` SÉPARÉ pour ce reset : le
    // changement de chapitre pouvait alors faire recomposer/relancer cet
    // effet-ci (`pagerState.currentPage, pageCount`) AVANT que l'effet de
    // reset n'ait eu la main (deux coroutines distinctes, ordre non
    // garanti) — avec `pageCount` déjà celui du NOUVEAU chapitre mais
    // `pagerState.currentPage` encore l'ancien index (page fantôme de
    // l'ancien chapitre), `currentPage >= pageCount` pouvait redevenir
    // vrai et redéclencher onNextChapter() une seconde fois, sautant le
    // chapitre qui vient tout juste de s'ouvrir. Le détecteur de
    // changement de chapitre est donc fusionné dans CET effet, comme
    // première instruction séquentielle d'une seule et même coroutine :
    // aucune autre branche de cet effet ne peut s'exécuter avant que le
    // reset n'ait eu lieu.
    //
    // Bug réel trouvé sur appareil (clignotement frénétique après un long
    // défilement puis bascule SCROLL → PAGED) : `previousChapterIndex`
    // était initialisé à `chapter?.index`, donc la TOUTE PREMIÈRE
    // composition du pager n'était PAS traitée comme un changement de
    // chapitre — l'effet émettait alors `onManualPageChange` pour la page
    // 0 (ramenant `currentSentenceIndex`, hérité du défilement, à la
    // première phrase) DANS LA MÊME phase que l'effet d'ancrage qui, lui,
    // scrolle vers la page de la position profonde. Deux coroutines aux
    // objectifs contraires (page 0 vs page profonde) s'invalidaient
    // mutuellement en boucle : le clignotement observé. En partant de
    // `null`, la première composition emprunte la branche « changement de
    // chapitre » (reset du pager, AUCUNE émission) et laisse l'effet
    // d'ancrage positionner seul le pager — même chemin, déjà éprouvé,
    // que le changement de chapitre réel.
    var previousChapterIndex by remember { mutableStateOf<Int?>(null) }
    // Même correctif, second versant : quand la mesure COMPLÈTE arrive
    // (mesure progressive 3a.3), `pageCount` change sans que l'utilisateur
    // n'ait swipé. Sans cette garde, l'effet émettait alors
    // `onManualPageChange` pour la page courante — ce qui écrasait la
    // position de reprise (`currentSentenceIndex` profond) AVANT que
    // l'effet d'ancrage n'ait pu positionner le pager. Un changement de
    // `pageCount` n'est jamais un swipe : on le détecte et on s'abstient
    // d'émettre.
    var previousPageCount by remember { mutableStateOf(-1) }

    LaunchedEffect(chapter?.index, pagerState.currentPage, pageCount) {
        if (chapter?.index != previousChapterIndex) {
            previousChapterIndex = chapter?.index
            previousPageCount = pageCount
            if (pagerState.currentPage != 0) {
                isProgrammaticPageChange = true
                try {
                    pagerState.scrollToPage(0)
                } finally {
                    isProgrammaticPageChange = false
                }
            }
            return@LaunchedEffect
        }
        if (pageCount != previousPageCount) {
            previousPageCount = pageCount
            return@LaunchedEffect
        }
        // Remonte la page réellement affichée — swipe manuel inclus,
        // pas seulement la progression pilotée par le TTS. Bug réel
        // trouvé sur appareil (lot 3b) : le compteur de la ligne de
        // statut restait figé à 1 pendant un swipe manuel, puisque
        // rien ne faisait remonter la position du pager avant ceci.
        onPageChanged(pagerState.currentPage)

        if (!isProgrammaticPageChange && chapter != null) {
            // P5 — le cran haptique de la page tournée est posé ICI, dans la
            // branche du swipe MANUEL, et pas sur `onPageChanged` ci-dessus :
            // ce dernier suit aussi les changements de page pilotés par la
            // narration, qui feraient vibrer l'appareil en continu pendant
            // une écoute — précisément quand l'utilisateur ne le touche pas.
            haptics.tick()
            val sentenceRange = pagination.sentenceRangeOf(chapter.index, pagerState.currentPage)
            if (!sentenceRange.isEmpty()) {
                lastManuallyEmittedSentenceIndex = sentenceRange.first
                onManualPageChange(sentenceRange.first)
            }
        }
    }

    // Ancrage de position (3a.1) : capturer currentSentenceIndex et
    // repositionner via pageIndexAt à chaque recalcul de pagination
    // (rotation, taille de police...) — jamais un index de page persisté.
    //
    // Ne réagit QU'à une mesure complète (isMeasurementComplete) : pendant
    // la mesure progressive (3a.3), `pageIndexAt` peut renvoyer
    // `pages.lastIndex` pour une phrase profonde non encore mesurée —
    // se recaler sur lui ferait sauter le pager vers la dernière page du
    // préfixe (page fausse, voire page vide). La remesure HUD (autrefois
    // cause de clignotement) a par ailleurs été supprimée à la racine
    // (ReaderScreen : HUD en overlay, zone de lecture à hauteur constante).
    LaunchedEffect(chapter?.index, pagination.measurement, currentSentenceIndex) {
        if (currentSentenceIndex == lastManuallyEmittedSentenceIndex) {
            // Écho de notre propre swipe (voir commentaire ci-dessus) :
            // le pager est déjà à la bonne page, ne pas le contredire.
            lastManuallyEmittedSentenceIndex = null
            return@LaunchedEffect
        }
        if (chapter != null && pagination.measurement != null && pageCount > 0 &&
            pagination.isMeasurementComplete(chapter)
        ) {
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

    // Déjà en offsets de caractère absolus (calés sur des bornes de mot
    // par PageBlock), aucune conversion à faire ici.
    val freeSelectedRangeState = rememberUpdatedState(freeSelectedRange)

    // Transition de chapitre par résistance spatiale (overscroll horizontal).
    val chapterTransition = remember { ChapterTransitionState() }
    var pagerWidthPx by remember { mutableStateOf(0) }
    LaunchedEffect(pagerWidthPx) {
        chapterTransition.thresholdPx = pagerWidthPx * 0.25f
    }

    val visualPull = remember { Animatable(0f) }
    // Lot 21 — le rebond élastique passe par Motion.gestureSpring (réduction
    // de mouvement système) et la préférence applicative reduceMotion (tâche
    // 4) : plus de spring en dur. Calculé dans la composition (spec @Composable),
    // consommé dans la coroutine du LaunchedEffect.
    val pullBackSpec = gesturePullBackSpec(reduceMotion)
    LaunchedEffect(chapterTransition.isDragging) {
        if (chapterTransition.isDragging) {
            snapshotFlow { chapterTransition.pullPx }.collect { visualPull.snapTo(it) }
        } else {
            visualPull.animateTo(0f, pullBackSpec)
        }
    }

    val latestHasPrevious = rememberUpdatedState(hasPreviousChapter)
    val latestHasNext = rememberUpdatedState(hasNextChapter)
    val latestChapterIndex = rememberUpdatedState(currentChapterIndex)
    val latestChapterCount = rememberUpdatedState(chapterCount)
    val latestOnPrevious = rememberUpdatedState(onPreviousChapter)
    val latestOnNext = rememberUpdatedState(onNextChapter)
    val latestIsChapterReady = rememberUpdatedState(isChapterReady)

    val chapterTransitionConnection = remember(chapterTransition, pagerState) {
        ChapterTransitionConnection(
            state = chapterTransition,
            orientation = Orientation.Horizontal,
            canPullPrevious = { pagerState.currentPage == 0 && latestHasPrevious.value },
            canPullNext = { pagerState.currentPage == pagerState.pageCount - 1 && latestHasNext.value },
            // Bug réel trouvé à l'audit : sans cette garde, un glissement de
            // sélection de texte au bord du chapitre pouvait être capté par
            // ce geste de tirage plutôt que par le champ de texte.
            isSelectionActive = { freeSelectedRangeState.value != null },
            onCommit = { direction ->
                // Parité avec le mode SCROLL (ReaderScreen) : bornage
                // défensif — canPullPrevious/canPullNext ne devraient jamais
                // laisser `target` sortir de [0, lastIndex], mais un état
                // transitoirement désynchronisé pendant une transition
                // rapide ne doit jamais produire un index de chapitre
                // invalide (sinon `beginLoading` bloque le spinner : voir
                // KDoc de `isChapterReady`).
                val target = when (direction) {
                    ChapterTransitionDirection.PREVIOUS -> (latestChapterIndex.value - 1).coerceAtLeast(0)
                    ChapterTransitionDirection.NEXT ->
                        (latestChapterIndex.value + 1).coerceAtMost(latestChapterCount.value - 1)
                }
                chapterTransition.beginLoading(target)
                if (direction == ChapterTransitionDirection.PREVIOUS) latestOnPrevious.value() else latestOnNext.value()
            },
            onCancel = { chapterTransition.cancel() },
        )
    }

    LaunchedEffect(chapterTransition.isLoading) {
        if (!chapterTransition.isLoading) return@LaunchedEffect
        val target = chapterTransition.targetChapterIndex
        val startedAt = SystemClock.uptimeMillis()
        snapshotFlow { latestIsChapterReady.value(target) }.first { it }
        val elapsed = SystemClock.uptimeMillis() - startedAt
        if (elapsed < ChapterTransitionMath.MIN_LOADING_MS) {
            delay(ChapterTransitionMath.MIN_LOADING_MS - elapsed)
        }
        chapterTransition.finish()
    }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { pagerWidthPx = it.width }) {
        HorizontalPager(
            state = pagerState,
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer { translationX = visualPull.value }
                .nestedScroll(chapterTransitionConnection),
            beyondViewportPageCount = 1,
        ) { pageIndex ->
            Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                if (chapter != null && currentMeasurement != null && pageIndex < pageCount) {
                val pageOffsetRange = pagination.pageOffsetRange(chapter.index, pageIndex)
                if (!pageOffsetRange.isEmpty()) {
                    val pageText = remember(currentMeasurement, pageOffsetRange, annotations) {
                        buildPageAnnotatedString(
                            full = currentMeasurement.annotatedString,
                            pageOffsetRange = pageOffsetRange,
                            chapterIndex = currentChapterIndex,
                            annotations = annotations,
                        )
                    }
                    PageBlock(
                        pageText = pageText,
                        pageOffsetRange = pageOffsetRange,
                        textStyle = renderTextStyle,
                        highlightedRange = highlightedRangeState,
                        isDisplayedPage = pageIndex == pagerState.currentPage,
                        isReadingRulerEnabled = isReadingRulerEnabled,
                        onCurrentLineY = onCurrentLineY,
                        freeSelectedRange = freeSelectedRangeState,
                        onFreeSelectionChanged = onFreeSelectionChanged,
                        onFreeSelectionCleared = onFreeSelectionCleared,
                        // Identité de la page émettrice injectée ici : c'est
                        // le seul endroit qui connaît à la fois le
                        // `pageOffsetRange` et le callback du parent.
                        onFreeSelectionBoundsInWindow = { bounds ->
                            onFreeSelectionBoundsInWindow(pageOffsetRange.first, bounds)
                        },
                        onClick = onClick,
                    )
                }
            }
        }
        }

        val transitionDirection = chapterTransition.direction
        if (transitionDirection != null) {
            ChapterTransitionIndicator(
                direction = transitionDirection,
                fraction = ChapterTransitionMath.fraction(visualPull.value, chapterTransition.thresholdPx),
                isLoading = chapterTransition.isLoading,
                reduceMotion = reduceMotion,
                contentColor = textColor,
                surfaceColor = surfaceColor,
                modifier = Modifier
                    .align(
                        if (transitionDirection == ChapterTransitionDirection.PREVIOUS)
                            Alignment.CenterStart else Alignment.CenterEnd
                    )
                    .padding(horizontal = 8.dp),
            )
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
    isDisplayedPage: Boolean,
    isReadingRulerEnabled: Boolean,
    onCurrentLineY: (Dp) -> Unit,
    freeSelectedRange: State<IntRange?>,
    onFreeSelectionChanged: (anchorOffset: Int, focusOffset: Int) -> Unit,
    onFreeSelectionCleared: () -> Unit,
    onFreeSelectionBoundsInWindow: (Rect?) -> Unit,
    onClick: () -> Unit,
) {
    // Keyé sur `pageOffsetRange`, comme `selection`/`pendingShowJob`
    // ci-dessous : évite un `TextLayoutResult`/des coordonnées d'une
    // AUTRE page qui persisteraient transitoirement si Compose réutilise
    // cette instance de composable pour un `pageOffsetRange` différent.
    var textLayoutResult by remember(pageOffsetRange) { mutableStateOf<TextLayoutResult?>(null) }
    var textCoordinates by remember(pageOffsetRange) { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current

    // Un `BasicTextField` en lecture seule délègue directement à la
    // sélection NATIVE de Compose (appui long calé sur le mot via
    // `getWordBoundary`, poignées de glissement caractère par caractère,
    // aucune réimplémentation gestuelle) — la seule source qui produit
    // un `TextRange` réellement exact.
    // Offsets LOCAUX à la page ; réinitialisée à chaque page (la
    // sélection ne survit pas un changement de page — bornée à la page,
    // jamais à cheval sur deux, limite structurelle du découpage).
    var localSelection by remember(pageOffsetRange) { mutableStateOf(TextRange.Zero) }

    // Phase 1 — état local strictement SUBORDONNÉ à l'appartenance
    // globale. `localSelection` n'est qu'un état visuel transitoire ; la
    // source de vérité de « qui possède la sélection » reste
    // `freeSelectedRange` (remonté par le ViewModel). Dès que l'état
    // global ne pointe plus dans cette page — devenu `null`, ou pointant
    // désormais vers une AUTRE page — la sélection rendue est
    // immédiatement `collapsed`, DANS LA MÊME recomposition. Dérivation
    // pure plutôt qu'un `snapshotFlow`/`LaunchedEffect` de
    // resynchronisation comme avant : un effet ne s'exécute qu'après la
    // composition, laissant une frame où les poignées natives restaient
    // affichées sur une sélection que le reste de l'écran avait déjà
    // oubliée — et le popup pouvait se rouvrir dans cet intervalle.
    val globalSelection = freeSelectedRange.value
    val ownsGlobalSelection = globalSelection != null &&
        globalSelection.first >= pageOffsetRange.first &&
        globalSelection.last <= pageOffsetRange.last
    val selection = if (ownsGlobalSelection) localSelection else TextRange.Zero
    // `toolbar` ci-dessous est `remember`é : il ne peut pas capturer
    // `selection` directement (val recalculé à chaque composition — il en
    // figerait à jamais la toute première valeur, `TextRange.Zero`), d'où
    // cet État relu à chaque appel.
    val selectionState = rememberUpdatedState(selection)

    val fieldValue = remember(pageText, selection) { TextFieldValue(pageText, selection) }

    // Phase 2 — visibilité du popup d'actions. Apparition pilotée par
    // `TextToolbar.showMenu()` (doigt levé), disparition par le
    // mouvement de la sélection (`onValueChange`, voir la justification
    // mesurée là-bas) — jamais par une réaction à `freeSelectedRange`.
    // Non-null ≡ popup visible : ces bornes fenêtre sont l'unique signal
    // transmis au parent (`onFreeSelectionBoundsInWindow`), qui monte le
    // popup si et seulement s'il les reçoit.
    var popupBoundsInWindow by remember(pageOffsetRange) { mutableStateOf<Rect?>(null) }
    val currentOnBoundsInWindow by rememberUpdatedState(onFreeSelectionBoundsInWindow)
    val ownsGlobalSelectionState = rememberUpdatedState(ownsGlobalSelection)

    /** Détruit le popup s'il est affiché, sans jamais réémettre inutilement vers le parent. */
    fun hidePopup() {
        if (popupBoundsInWindow == null) return
        popupBoundsInWindow = null
        currentOnBoundsInWindow(null)
    }

    // Phase 3 — départ d'écran (page adjacente gardée montée par
    // `beyondViewportPageCount = 1`, jamais détruite par un simple
    // swipe) : nettoyage SILENCIEUX complet — popup détruit, sélection
    // locale rétractée, état global purgé si cette page en était
    // propriétaire. Corrige la « sélection fantôme » : sans ceci,
    // `localSelection` restait non-collapsed indéfiniment sur cette
    // instance conservée montée hors écran, ce qui bloquait tout tap
    // ultérieur sur cette page et pouvait faire réapparaître l'ancien
    // surlignage SANS popup en reswipant vers elle.
    LaunchedEffect(isDisplayedPage) {
        if (!isDisplayedPage) {
            hidePopup()
            localSelection = TextRange.Zero
            // Purge de l'état GLOBAL uniquement si cette page en était
            // encore propriétaire : après une action du popup (Phase 4),
            // `localSelection` peut rester non-collapsed alors que la
            // sélection globale appartient déjà à une autre unité — s'y
            // fier ici effacerait la sélection de quelqu'un d'autre.
            if (ownsGlobalSelectionState.value) onFreeSelectionCleared()
        }
    }

    // Le champ dessine lui-même un fond de sélection natif sur
    // `fieldValue.selection`, qui doublerait le surlignage posé par
    // `drawAbsoluteRangeHighlight` ci-dessous (seule source qui reste
    // correcte une fois le champ défocalisé, ex. popup de sélection
    // ouvert). Fond neutralisé, poignées de glissement inchangées (leur
    // couleur ne dépend pas de `backgroundColor`).
    val selectionColors = LocalTextSelectionColors.current
    val handlesOnlySelectionColors = remember(selectionColors) {
        TextSelectionColors(handleColor = selectionColors.handleColor, backgroundColor = Color.Transparent)
    }

    // Palier 3f.2 (correctif du menu système en double) — `BasicTextField`
    // appelle `LocalTextToolbar.current.showMenu(rect, ...)` pour afficher
    // SON menu Copier/Coller. Sans interception, c'est le vrai
    // `TextToolbar` de la plateforme (barre système blanche, `ActionMode`)
    // qui s'affiche, EN PLUS du popup sombre de l'app
    // (`SelectionActionPopup`) — d'où le doublon constaté sur appareil.
    // Depuis la Phase 2, ce popup n'est plus monté indépendamment de ce
    // toolbar : c'est CE toolbar qui pilote seul son cycle de vie
    // (`showMenu`/`hide` ci-dessous). On ne délègue
    // donc JAMAIS à `defaultToolbar` — le menu système ne doit plus jamais
    // apparaître. `status` fixé à `Hidden` en conséquence : de son propre
    // point de vue, ce `TextToolbar` ne montre jamais rien.
    //
    // Bug réel trouvé sur appareil (popup sombre à une position aléatoire
    // à l'écran) : le `rect` fourni en paramètre de `showMenu` n'est PAS
    // fiable comme bornes de la sélection réelle — c'est l'ancre du menu
    // système que `BasicTextField` aurait affiché, pas garanti d'être les
    // bornes pixel-exactes de la sélection rendue. Recalculé ici via le
    // même mécanisme que `rangeBoundsInWindow` (`getPathForRange` +
    // `localToWindow`), à partir de la sélection LOCALE réellement
    // affichée (`selection`, pas le `rect` reçu) — jamais le paramètre
    // `rect` lui-même.
    //
    // Phase 2 — cycle de vie du popup, calqué à l'identique sur celui du
    // toolbar natif : `hide()` = « le doigt est posé / la sélection
    // bouge » (glissement de poignée en cours : l'écran doit rester
    // dégagé pour la loupe native), `showMenu()` = « le doigt est levé,
    // la sélection est arrêtée ». Aucun anti-rebond côté appelant :
    // `BasicTextField` appelle déjà `hide()` au début d'un glissement et
    // `showMenu()` à sa fin, c'est LUI qui porte la notion de geste
    // terminé — la dupliquer par un délai ne faisait que retarder
    // l'apparition du popup après un relâchement réel.
    val toolbar = remember(pageOffsetRange) {
        object : TextToolbar {
            override val status: TextToolbarStatus = TextToolbarStatus.Hidden

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?,
            ) {
                val currentSelection = selectionState.value
                val absolute = if (currentSelection.collapsed) {
                    null
                } else {
                    (pageOffsetRange.first + currentSelection.min)..(pageOffsetRange.first + currentSelection.max - 1)
                }
                val windowRect = rangeBoundsInWindow(textLayoutResult, textCoordinates, pageOffsetRange, absolute)
                popupBoundsInWindow = windowRect
                currentOnBoundsInWindow(windowRect)
            }

            override fun hide() {
                if (popupBoundsInWindow == null) return
                popupBoundsInWindow = null
                currentOnBoundsInWindow(null)
            }
        }
    }

    CompositionLocalProvider(
        LocalTextSelectionColors provides handlesOnlySelectionColors,
        LocalTextToolbar provides toolbar,
    ) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                // Phase 3 — `BasicTextField` consomme en interne les taps
                // pour positionner son curseur (même en lecture seule) :
                // un `pointerInput` sibling ne les voit jamais (avalés
                // avant lui dans l'arène de gestes Compose). Plutôt que de
                // lutter contre cette priorité, `onValueChange` EST la
                // seule source de vérité du tap — aucun
                // `detectTapGestures` concurrent nulle part sur ce champ.
                //
                // Passer de non-collapsed à collapsed = annulation
                // EXPLICITE par l'utilisateur (tap hors de la sélection,
                // tap dessus, ou poignée ramenée jusqu'à la refermer) :
                // purge globale + destruction du popup, immédiatement.
                // Un tap alors qu'il n'y avait rien de sélectionné ne
                // fait que basculer le HUD.
                val wasSelecting = !selection.collapsed
                val selectionChanged = newValue.selection != selection
                localSelection = newValue.selection
                if (newValue.selection.collapsed) {
                    if (wasSelecting) {
                        onFreeSelectionCleared()
                        hidePopup()
                    }
                    onClick()
                } else {
                    // Phase 2, phase « glissement » — mesuré sur appareil
                    // (V2206, Android 14) : `TextToolbar.hide()` n'est
                    // JAMAIS appelé pendant le glissement d'une poignée, et
                    // `showMenu()` ne l'est qu'au relâchement du doigt. Le
                    // toolbar ne fournit donc à lui seul aucun signal de
                    // « geste en cours » — s'y fier laissait le popup
                    // affiché par-dessus la loupe native pendant tout le
                    // glissement.
                    //
                    // Le seul signal fiable de ce geste est ICI : une
                    // sélection qui CHANGE alors qu'elle reste non vide,
                    // c'est l'utilisateur en train de l'ajuster. Le popup
                    // se masque donc à chaque mouvement, et seul
                    // `showMenu()` (doigt levé) le fait réapparaître —
                    // exactement le découpage voulu, branché sur les
                    // évènements qui existent réellement plutôt que sur
                    // ceux que l'API laisse supposer.
                    if (selectionChanged) hidePopup()
                    val min = newValue.selection.min
                    val max = newValue.selection.max
                    onFreeSelectionChanged(pageOffsetRange.first + min, pageOffsetRange.first + max - 1)
                }
            },
            readOnly = true,
            textStyle = textStyle,
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
                // Palier 3f.4 (première passe, pas de spike TalkBack
                // dédié — voir CHIFFRAGE_LOT_3F_SELECTION_MOT.md) : le tap
                // tactile réel passe uniquement par `onValueChange`
                // ci-dessus (Phase 3 — aucun
                // `pointerInput`/`detectTapGestures` sibling ici, il
                // perdait systématiquement l'arène de gestes face au tap
                // interne de `BasicTextField`).
                // L'action « activer » que TalkBack synthétise
                // (double-tap après exploration) ne déclenche en revanche
                // AUCUN évènement tactile ni `onValueChange` — sans cette
                // action sémantique dédiée, un utilisateur TalkBack ne
                // pouvait pas du tout rappeler le HUD depuis la zone de
                // lecture. Même garde que la sélection en cours
                // (`selection.collapsed`), pour la même raison.
                .semantics {
                    onClick(label = "Afficher ou masquer les commandes") {
                        if (!selection.collapsed) return@onClick false
                        onClick()
                        true
                    }
                }
                .drawWithContent {
                    textLayoutResult?.let { layout ->
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
}

internal fun rangeBoundsInWindow(
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

internal fun DrawScope.drawAbsoluteRangeHighlight(
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

internal val WordHighlightColor = Color(0xFFFFEB3B)
internal val SelectionHighlightColor = Color(0x664FC3F7)

internal fun buildPageAnnotatedString(
    full: AnnotatedString,
    pageOffsetRange: IntRange,
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

    // Correctif diagnostic 3f.2 — l'ancienne version parcourait les
    // PHRASES de la page et peignait le fond sur la phrase ENTIÈRE dès
    // qu'une annotation la touchait (test de simple chevauchement), sans
    // rapport avec les offsets réels de l'annotation. Une annotation au
    // mot (sélection libre 3f.1) se retrouvait donc affichée comme si
    // toute la phrase avait été surlignée. Ici, chaque annotation est
    // intersectée directement avec la page — aucune notion de phrase.
    val overlapping = annotations.filter { annotation ->
        annotation.startLocator.chapterIndex == chapterIndex &&
            annotation.startLocator.charOffset < endExclusive &&
            annotation.endLocator.charOffset > startInclusive
    }
    if (overlapping.isEmpty()) return base

    return buildAnnotatedString {
        append(base)
        for (annotation in overlapping) {
            val localStart = (maxOf(annotation.startLocator.charOffset, startInclusive) - startInclusive)
                .coerceIn(0, base.length)
            val localEndExclusive = (minOf(annotation.endLocator.charOffset, endExclusive) - startInclusive)
                .coerceIn(localStart, base.length)
            if (localStart < localEndExclusive) {
                addStyle(annotationSpanStyle(annotation.kind, annotation.color), localStart, localEndExclusive)
            }
        }
    }
}
