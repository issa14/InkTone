package com.inktone.feature.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Chapter
import com.inktone.feature.reader.pagination.ChapterPaginationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    textColor: Color,
    isReadingRulerEnabled: Boolean,
    onClick: () -> Unit,
    onNextChapter: () -> Unit,
    onCurrentLineY: (Dp) -> Unit,
    onPageChanged: (Int) -> Unit = {},
    onManualPageChange: (sentenceIndex: Int) -> Unit = {},
    // Sélection libre au mot, offsets de caractère absolus dans le
    // chapitre.
    freeSelectedRange: IntRange? = null,
    onFreeSelectionChanged: (anchorOffset: Int, focusOffset: Int) -> Unit = { _, _ -> },
    onFreeSelectionCleared: () -> Unit = {},
    onFreeSelectionBoundsInWindow: (Rect?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val renderTextStyle = remember(pagination.baseTextStyle, textColor) {
        pagination.baseTextStyle.copy(color = textColor)
    }

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

    // Déjà en offsets de caractère absolus (calés sur des bornes de mot
    // par PageBlock), aucune conversion à faire ici.
    val freeSelectedRangeState = rememberUpdatedState(freeSelectedRange)

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
    ) { pageIndex ->
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                        onFreeSelectionBoundsInWindow = onFreeSelectionBoundsInWindow,
                        onClick = onClick,
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
    isDisplayedPage: Boolean,
    isReadingRulerEnabled: Boolean,
    onCurrentLineY: (Dp) -> Unit,
    freeSelectedRange: State<IntRange?>,
    onFreeSelectionChanged: (anchorOffset: Int, focusOffset: Int) -> Unit,
    onFreeSelectionCleared: () -> Unit,
    onFreeSelectionBoundsInWindow: (Rect?) -> Unit,
    onClick: () -> Unit,
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current

    // Un `BasicTextField` en lecture seule délègue directement à la
    // sélection NATIVE de Compose (appui long calé sur le mot via
    // `getWordBoundary`, poignées de glissement caractère par caractère,
    // aucune réimplémentation gestuelle) — la seule source qui produit
    // un `TextRange` réellement exact.
    // Offsets LOCAUX à la page ; réinitialisée à chaque page (la
    // sélection ne survit pas un changement de page — bornée à la page,
    // jamais à cheval sur deux, limite structurelle du découpage).
    var selection by remember(pageOffsetRange) { mutableStateOf(TextRange.Zero) }
    val fieldValue = remember(pageText, selection) { TextFieldValue(pageText, selection) }

    // Anti-rebond de `showMenu` (voir le `TextToolbar` plus bas) — déclaré
    // ici pour pouvoir aussi être annulé par l'effet de nettoyage
    // `isDisplayedPage` ci-dessous, sinon un affichage différé pourrait
    // encore se déclencher après que la page a quitté l'écran.
    val coroutineScope = rememberCoroutineScope()
    var pendingShowJob by remember(pageOffsetRange) { mutableStateOf<Job?>(null) }

    // Resynchronisation dans l'autre sens : si `freeSelectedRange` (état
    // remonté par le ViewModel) redevient `null` ailleurs (bouton Annuler
    // du popup, `ClearFreeSelection`), la sélection LOCALE du champ doit
    // être effacée aussi, sinon les poignées natives resteraient
    // affichées sur une sélection que le reste de l'écran a déjà
    // oubliée. Les BORNES du popup, elles, viennent désormais de
    // `showMenu` ci-dessous (rect natif), pas d'un recalcul ici.
    LaunchedEffect(isDisplayedPage, pageOffsetRange) {
        if (!isDisplayedPage) {
            pendingShowJob?.cancel()
            pendingShowJob = null
            onFreeSelectionBoundsInWindow(null)
            return@LaunchedEffect
        }
        snapshotFlow { freeSelectedRange.value }.collect { absolute ->
            if (absolute == null && selection != TextRange.Zero) {
                selection = TextRange.Zero
            }
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
    // qui s'affiche, EN PLUS du popup sombre de l'app (`SelectionActionPopup`,
    // toujours monté dès que `freeSelectedRange != null`, indépendamment de
    // ce toolbar) — d'où le doublon constaté sur appareil. On ne délègue
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
    // Bug réel trouvé sur appareil (popup sombre visible PENDANT le
    // glissement d'une poignée, recouvrant la loupe native) : `showMenu`
    // se déclenche à CHAQUE étape du glissement (repositionnement continu,
    // pas seulement au relâchement) quand l'appui long et le glissement
    // d'extension se font en un seul geste continu (sans lever le doigt
    // entre les deux) — `hide()` n'a alors aucune fenêtre "glissement en
    // cours" à signaler puisqu'il n'y a pas d'évènement de relâchement
    // intermédiaire. Non observable en lisant le code seul (dépend de la
    // cadence réelle des appels internes de `BasicTextField`, vérifiée sur
    // appareil, pas supposée) : anti-rebond côté appelant plutôt qu'une
    // dépendance à un relâchement qui peut ne jamais survenir en milieu de
    // geste. `hide()` reste instantané (glissement démarré ou sélection
    // effacée : aucune raison d'attendre) ; `showMenu` calcule les bornes
    // immédiatement (état de sélection à cet instant précis) mais ne les
    // transmet qu'une fois les appels retombés silencieux pendant
    // `SelectionPopupSettleDelayMs` — un nouvel appel (donc glissement
    // toujours actif) annule et relance le délai avec de nouvelles bornes.
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
                val currentSelection = selection
                val absolute = if (currentSelection.collapsed) {
                    null
                } else {
                    (pageOffsetRange.first + currentSelection.min)..(pageOffsetRange.first + currentSelection.max - 1)
                }
                val windowRect = rangeBoundsInWindow(textLayoutResult, textCoordinates, pageOffsetRange, absolute)
                pendingShowJob?.cancel()
                pendingShowJob = coroutineScope.launch {
                    delay(SelectionPopupSettleDelayMs)
                    onFreeSelectionBoundsInWindow(windowRect)
                }
            }

            override fun hide() {
                pendingShowJob?.cancel()
                pendingShowJob = null
                onFreeSelectionBoundsInWindow(null)
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
                // Bug réel trouvé sur appareil : un tap sur la sélection
                // pour l'annuler est traité EN INTERNE par BasicTextField
                // (collapse de la sélection) — notre pointerInput sibling
                // ci-dessous ne voit jamais cet évènement précis (avalé
                // avant lui), donc `onClick` n'était jamais appelé et le
                // HUD ne revenait pas après une annulation par tap.
                // Détecté ici via la transition non-collapsed → collapsed
                // (seule source fiable de ce signal), pas via le geste.
                val wasCollapsed = selection.collapsed
                selection = newValue.selection
                if (newValue.selection.collapsed) {
                    onFreeSelectionCleared()
                    if (!wasCollapsed) {
                        onClick()
                    }
                } else {
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
                .pointerInput(pageOffsetRange) {
                    detectTapGestures(
                        onTap = {
                            // Ne rien faire tant qu'une sélection libre est
                            // active (poignées visibles) : appeler `onClick`
                            // déclencherait potentiellement
                            // `handleReadingAreaTap` (bascule du HUD, donc
                            // une remesure de pagination qui recrée ce
                            // champ) EN PLEIN geste de sélection — c'est ce
                            // qui faisait disparaître les poignées natives
                            // trouvé sur appareil, pas la reconnaissance du
                            // geste d'appui long elle-même (délégué en
                            // interne par `BasicTextField`, jamais
                            // intercepté ici).
                            if (!selection.collapsed) return@detectTapGestures
                            onClick()
                        },
                    )
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

/** Anti-rebond de `showMenu` (voir `PageBlock`) — assez court pour rester imperceptible après un relâchement réel, assez long pour couvrir l'intervalle entre deux étapes d'un glissement actif. */
private const val SelectionPopupSettleDelayMs = 200L

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
                addStyle(SpanStyle(background = annotation.color.toComposeColor()), localStart, localEndExclusive)
            }
        }
    }
}
