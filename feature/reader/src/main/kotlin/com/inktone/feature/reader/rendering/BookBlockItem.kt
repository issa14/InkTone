package com.inktone.feature.reader.rendering

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.BookBlock
import com.inktone.domain.service.EpubResourceResolver
import com.inktone.feature.reader.SelectionHighlightColor
import com.inktone.feature.reader.WordHighlightColor
import com.inktone.feature.reader.absoluteRangePath
import com.inktone.feature.reader.annotationSpanStyle
import com.inktone.feature.reader.rangeBoundsInWindow
import com.inktone.feature.reader.toComposeColor

/**
 * Rendu d'un [BookBlock] — unité atomique du flux de lecture.
 *
 * ## Pont TTS ↔ UI — Algorithme O(log n)
 *
 * Quand le TTS signale un mot à l'offset global `charOffset` :
 * 1. Trouver le bloc contenant cet offset via recherche dichotomique
 *    sur `blocks.map { it.globalOffsetRange!!.start }`.
 * 2. `val localOffset = charOffset - block.globalOffsetRange!!.start`.
 * 3. Appliquer le surlignage dans le [BasicTextField] de CE bloc uniquement.
 *
 * Cette recherche est faite UNE FOIS par `ReaderScreen` (offset absolu →
 * `IntRange` absolu, cf. `absoluteHighlightedRange` du mode SCROLL) ; ce
 * composable ne fait ensuite qu'une soustraction locale par bloc,
 * exactement comme `PageBlock` (mode PAGED) le fait par page.
 *
 * ## Sélection libre + popup d'action
 *
 * Sélection non inter-bloc (limite connue, identique au mode PAGED par
 * page — voir Plan v3 § « Limites connues »). AUDIT_REACTIVITE_UX §4.1 :
 * seul le [BookBlock.ParagraphBlock] qui possède la sélection libre en
 * cours monte un [BasicTextField] indépendant ([ParagraphBlockField]) ; les
 * autres rendent par `Text` ([ParagraphBlockDisplay]), qui démarre la
 * sélection par un appui long. La logique de sélection/toolbar/surlignage
 * du bloc sélectionné est le pendant, à l'échelle du BLOC, de `PageBlock`
 * dans `PagedChapterContent.kt` (mêmes fonctions partagées :
 * [rangeBoundsInWindow], [absoluteRangePath], [WordHighlightColor],
 * [SelectionHighlightColor]).
 *
 * @param block Le bloc à rendre.
 * @param baseTextStyle Style de texte de base (taille, police, couleur du thème).
 * @param resolver Résolveur de ressources EPUB (pour les images).
 * @param publicationId ID de la publication (pour les images).
 * @param chapterIndex Index du chapitre courant (filtrage des [annotations]).
 * @param annotations Annotations du chapitre courant, pour le fond de surlignage persistant.
 * @param highlightedRange Offset absolu (chapitre) du mot actuellement prononcé par le TTS, ou null.
 * @param freeSelectedRange Offset absolu (chapitre) de la sélection libre active, ou null.
 * @param onFreeSelectionChanged Sélection libre modifiée (offsets absolus inclusifs).
 * @param onFreeSelectionCleared Sélection libre annulée explicitement par l'utilisateur.
 * @param onFreeSelectionBoundsInWindow Bornes fenêtre du popup d'action (null = masqué),
 *   identifiées par [BookBlock.globalOffsetRange]`.first` comme clé de propriétaire.
 * @param onClick Bascule HUD/topbar — tap hors sélection.
 * @param isReadingRulerEnabled Si vrai, remonte [onCurrentLineY] pour ce bloc quand il porte le mot actif.
 */
@Composable
fun BookBlockItem(
    block: BookBlock,
    baseTextStyle: TextStyle,
    resolver: EpubResourceResolver? = null,
    publicationId: String = "",
    chapterIndex: Int = 0,
    annotations: List<Annotation> = emptyList(),
    highlightedRange: State<IntRange?> = rememberUpdatedState(null),
    freeSelectedRange: State<IntRange?> = rememberUpdatedState(null),
    onFreeSelectionChanged: (anchorOffset: Int, focusOffset: Int) -> Unit = { _, _ -> },
    onFreeSelectionCleared: () -> Unit = {},
    onFreeSelectionBoundsInWindow: (ownerKey: Int, bounds: Rect?) -> Unit = { _, _ -> },
    onClick: () -> Unit = {},
    // Lot 23, tâche 11 — un tap qui tombe DANS une annotation déjà posée
    // déclenche ce callback avec ses bornes fenêtre plutôt que [onClick]
    // (bascule HUD). `bounds` peut être `null` si le layout n'est pas
    // encore disponible — l'appelant doit alors ignorer l'appel plutôt que
    // d'afficher un popup mal positionné.
    onAnnotationTapped: (Annotation, Rect?) -> Unit = { _, _ -> },
    isReadingRulerEnabled: Boolean = false,
    onCurrentLineY: (Dp) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (block) {
        is BookBlock.ParagraphBlock -> {
            val textStyle = BookBlockStyleMapper.textStyleFor(block, baseTextStyle)
            val blockText = remember(block.richText, annotations, chapterIndex) {
                buildBlockAnnotatedString(block, chapterIndex, annotations)
            }
            ParagraphBlockText(
                blockText = blockText,
                blockOffsetRange = block.globalOffsetRange,
                plainText = block.richText.plainText,
                textStyle = textStyle,
                annotations = annotations,
                chapterIndex = chapterIndex,
                highlightedRange = highlightedRange,
                freeSelectedRange = freeSelectedRange,
                onFreeSelectionChanged = onFreeSelectionChanged,
                onFreeSelectionCleared = onFreeSelectionCleared,
                onFreeSelectionBoundsInWindow = { bounds ->
                    onFreeSelectionBoundsInWindow(block.globalOffsetRange.first, bounds)
                },
                onClick = onClick,
                onAnnotationTapped = onAnnotationTapped,
                isReadingRulerEnabled = isReadingRulerEnabled,
                onCurrentLineY = onCurrentLineY,
                modifier = modifier,
            )
        }

        is BookBlock.HeadingBlock -> {
            val textStyle = BookBlockStyleMapper.textStyleFor(block, baseTextStyle)
            Text(
                text = block.richText.plainText,
                style = textStyle,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp)
                    .semantics { heading() },
            )
        }

        is BookBlock.ImageBlock -> {
            val imgWidth = block.intrinsicWidth
            val imgHeight = block.intrinsicHeight
            // Bug réel trouvé sur appareil : une couverture SVG a des
            // attributs width/height en PIXELS (ex. 479x706), pas en dp —
            // `Modifier.size(imgWidth.dp, imgHeight.dp)` traitait ces
            // pixels comme des dp, produisant soit une boîte énorme
            // débordant l'écran, soit — si un SVG déclare width/height
            // sans viewBox et qu'un parent en amont impose une contrainte
            // nulle — une mesure à 0x0 qui fait disparaître l'image sans
            // erreur visible. Modifier.aspectRatio garde le ratio réel de
            // l'image tout en occupant la largeur disponible du
            // conteneur (réactif, jamais une taille fixe en pixels bruts).
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .then(
                        if (imgWidth != null && imgHeight != null && imgWidth > 0 && imgHeight > 0) {
                            Modifier.aspectRatio(imgWidth.toFloat() / imgHeight.toFloat())
                        } else {
                            Modifier.heightIn(min = 100.dp, max = 300.dp)
                        },
                    )
                    .semantics {
                        block.alt?.let { contentDescription = it }
                    },
            ) {
                if (resolver != null && publicationId.isNotEmpty()) {
                    AsyncImage(
                        model = EpubImageKey(publicationId, block.href),
                        contentDescription = block.alt,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // Placeholder si pas de resolver (fallback)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .semantics {
                                block.alt?.let { contentDescription = it }
                                    ?: run { contentDescription = "Image" }
                            },
                    )
                }
            }
        }

        is BookBlock.SeparatorBlock -> {
            HorizontalDivider(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .semantics { invisibleToUser() },
                color = Color.Gray.copy(alpha = 0.3f),
            )
        }
    }
}

/**
 * Fond d'annotation persistant pour un [BookBlock.ParagraphBlock] —
 * pendant, à l'échelle du bloc, de `buildPageAnnotatedString`
 * (`PagedChapterContent.kt`) à l'échelle de la page.
 */
private fun buildBlockAnnotatedString(
    block: BookBlock.ParagraphBlock,
    chapterIndex: Int,
    annotations: List<Annotation>,
): AnnotatedString {
    val base = BookBlockStyleMapper.buildAnnotatedString(block.richText)
    val range = block.globalOffsetRange
    val overlapping = annotations.filter { annotation ->
        annotation.startLocator.chapterIndex == chapterIndex &&
            annotation.startLocator.charOffset < range.last + 1 &&
            annotation.endLocator.charOffset > range.first
    }
    if (overlapping.isEmpty()) return base

    return buildAnnotatedString {
        append(base)
        for (annotation in overlapping) {
            val localStart = (maxOf(annotation.startLocator.charOffset, range.first) - range.first)
                .coerceIn(0, base.length)
            val localEndExclusive = (minOf(annotation.endLocator.charOffset, range.last + 1) - range.first)
                .coerceIn(localStart, base.length)
            if (localStart < localEndExclusive) {
                addStyle(annotationSpanStyle(annotation.kind, annotation.color), localStart, localEndExclusive)
            }
        }
    }
}

/**
 * Lot 23, tâche 11 — l'annotation dont la plage contient [globalOffset]
 * dans [chapterIndex], ou `null`. Bornes en demi-ouvert
 * `[startLocator, endLocator[`, même convention que
 * [buildBlockAnnotatedString].
 */
internal fun annotationAtOffset(annotations: List<Annotation>, chapterIndex: Int, globalOffset: Int): Annotation? =
    annotations.firstOrNull { annotation ->
        annotation.startLocator.chapterIndex == chapterIndex &&
            annotation.startLocator.charOffset <= globalOffset &&
            annotation.endLocator.charOffset > globalOffset
    }

/**
 * Point d'entrée pour un [BookBlock.ParagraphBlock] : dispatche entre
 * [ParagraphBlockDisplay] (rendu `Text`, par défaut) et [ParagraphBlockField]
 * (`BasicTextField`, sélection native) selon que CE bloc possède ou non la
 * sélection libre en cours.
 *
 * AUDIT_REACTIVITE_UX §4.1 — un `BasicTextField` en lecture seule (focus,
 * session d'entrée, poignées de sélection, sémantique éditable) coûte
 * nettement plus qu'un `Text`. Avant cette correction, CHAQUE bloc visible
 * en était un, même sans sélection en cours : en défilement rapide, c'était
 * le prix payé par chaque paragraphe qui entrait dans la fenêtre visible.
 * Il n'y a jamais qu'une sélection libre à la fois, et jamais inter-bloc
 * (limite déjà documentée) : au plus UN bloc a donc besoin d'un
 * `BasicTextField` à un instant donné — celui qui la possède.
 */
@Composable
private fun ParagraphBlockText(
    blockText: AnnotatedString,
    blockOffsetRange: IntRange,
    plainText: String,
    textStyle: TextStyle,
    annotations: List<Annotation>,
    chapterIndex: Int,
    highlightedRange: State<IntRange?>,
    freeSelectedRange: State<IntRange?>,
    onFreeSelectionChanged: (anchorOffset: Int, focusOffset: Int) -> Unit,
    onFreeSelectionCleared: () -> Unit,
    onFreeSelectionBoundsInWindow: (Rect?) -> Unit,
    onClick: () -> Unit,
    onAnnotationTapped: (Annotation, Rect?) -> Unit,
    isReadingRulerEnabled: Boolean,
    onCurrentLineY: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val globalSelection = freeSelectedRange.value
    val ownsGlobalSelection = globalSelection != null &&
        globalSelection.first >= blockOffsetRange.first &&
        globalSelection.last <= blockOffsetRange.last

    if (ownsGlobalSelection) {
        ParagraphBlockField(
            blockText = blockText,
            blockOffsetRange = blockOffsetRange,
            plainText = plainText,
            textStyle = textStyle,
            annotations = annotations,
            chapterIndex = chapterIndex,
            highlightedRange = highlightedRange,
            freeSelectedRange = freeSelectedRange,
            onFreeSelectionChanged = onFreeSelectionChanged,
            onFreeSelectionCleared = onFreeSelectionCleared,
            onFreeSelectionBoundsInWindow = onFreeSelectionBoundsInWindow,
            onClick = onClick,
            onAnnotationTapped = onAnnotationTapped,
            isReadingRulerEnabled = isReadingRulerEnabled,
            onCurrentLineY = onCurrentLineY,
            modifier = modifier,
        )
    } else {
        ParagraphBlockDisplay(
            blockText = blockText,
            blockOffsetRange = blockOffsetRange,
            plainText = plainText,
            textStyle = textStyle,
            annotations = annotations,
            chapterIndex = chapterIndex,
            highlightedRange = highlightedRange,
            onFreeSelectionChanged = onFreeSelectionChanged,
            onClick = onClick,
            onAnnotationTapped = onAnnotationTapped,
            isReadingRulerEnabled = isReadingRulerEnabled,
            onCurrentLineY = onCurrentLineY,
            modifier = modifier,
        )
    }
}

/**
 * Rendu léger par `Text` d'un [BookBlock.ParagraphBlock] qui ne possède pas
 * la sélection libre en cours — le cas de la quasi-totalité des blocs à
 * tout instant. Surlignage mot-à-mot TTS et détection de tap sur annotation
 * conservés à l'identique de [ParagraphBlockField] ; la sélection démarre
 * ici par un appui long, qui calcule la limite de mot ([TextLayoutResult.getWordBoundary])
 * et la publie via [onFreeSelectionChanged] — ce bloc devient alors
 * propriétaire de la sélection globale et [ParagraphBlockText] le remonte
 * en [ParagraphBlockField] au prochain recomposition, poignées déjà
 * positionnées sur le mot visé (voir la valeur initiale de `localSelection`
 * dans [ParagraphBlockField]).
 */
@Composable
private fun ParagraphBlockDisplay(
    blockText: AnnotatedString,
    blockOffsetRange: IntRange,
    plainText: String,
    textStyle: TextStyle,
    annotations: List<Annotation>,
    chapterIndex: Int,
    highlightedRange: State<IntRange?>,
    onFreeSelectionChanged: (anchorOffset: Int, focusOffset: Int) -> Unit,
    onClick: () -> Unit,
    onAnnotationTapped: (Annotation, Rect?) -> Unit,
    isReadingRulerEnabled: Boolean,
    onCurrentLineY: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    var textLayoutResult by remember(blockOffsetRange) { mutableStateOf<TextLayoutResult?>(null) }
    var textCoordinates by remember(blockOffsetRange) { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current

    // Même mécanisme que ParagraphBlockField (§4.2 de l'audit) : lecture de
    // highlightedRange et écriture de onCurrentLineY hors de la phase de
    // layout, dérivées dans un effet à partir du TextLayoutResult capturé.
    LaunchedEffect(textLayoutResult, highlightedRange.value, isReadingRulerEnabled, blockOffsetRange) {
        if (!isReadingRulerEnabled) return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        val absRange = highlightedRange.value ?: return@LaunchedEffect
        val local = absRange.first - blockOffsetRange.first
        if (local in 0 until layout.layoutInput.text.length) {
            val line = layout.getLineForOffset(local)
            onCurrentLineY(with(density) { layout.getLineTop(line).toDp() })
        }
    }

    Text(
        text = blockText,
        style = textStyle,
        onTextLayout = { layout -> textLayoutResult = layout },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .onGloballyPositioned { textCoordinates = it }
            .semantics {
                contentDescription = plainText
                onClick(label = "Afficher ou masquer les commandes") {
                    onClick()
                    true
                }
            }
            .pointerInput(blockOffsetRange) {
                detectTapGestures(
                    onTap = { position ->
                        val layout = textLayoutResult ?: run {
                            onClick()
                            return@detectTapGestures
                        }
                        val globalOffset = blockOffsetRange.first + layout.getOffsetForPosition(position)
                        val tappedAnnotation = annotationAtOffset(annotations, chapterIndex, globalOffset)
                        if (tappedAnnotation != null) {
                            val absolute = maxOf(tappedAnnotation.startLocator.charOffset, blockOffsetRange.first)..
                                (minOf(tappedAnnotation.endLocator.charOffset, blockOffsetRange.last + 1) - 1)
                            val bounds = rangeBoundsInWindow(layout, textCoordinates, blockOffsetRange, absolute)
                            onAnnotationTapped(tappedAnnotation, bounds)
                        } else {
                            onClick()
                        }
                    },
                    onLongPress = { position ->
                        val layout = textLayoutResult ?: return@detectTapGestures
                        val localOffset = layout.getOffsetForPosition(position)
                        val boundary = layout.getWordBoundary(localOffset)
                        if (boundary.collapsed) return@detectTapGestures
                        onFreeSelectionChanged(
                            blockOffsetRange.first + boundary.start,
                            blockOffsetRange.first + boundary.end - 1,
                        )
                    },
                )
            }
            .drawWithCache {
                val layout = textLayoutResult
                val wordPath = layout?.let { l ->
                    highlightedRange.value?.let { absoluteRangePath(l, blockOffsetRange, it) }
                }
                onDrawBehind {
                    wordPath?.let { drawPath(it, WordHighlightColor) }
                }
            },
    )
}

/**
 * `BasicTextField` en lecture seule d'un [BookBlock.ParagraphBlock], avec
 * sélection native, popup d'action et surlignage mot-à-mot TTS — pendant
 * de `PageBlock` (`PagedChapterContent.kt`) à l'échelle du bloc plutôt
 * que de la page. Voir les commentaires de `PageBlock` pour la
 * justification détaillée de chaque choix (repris à l'identique ici).
 *
 * Monté uniquement pour le bloc qui possède la sélection libre en cours —
 * voir [ParagraphBlockText] (audit §4.1). `freeSelectedRange` n'est donc
 * plus consulté ici que pour amorcer `localSelection` à la sélection déjà
 * publiée (le mot visé par l'appui long dans [ParagraphBlockDisplay]), pas
 * pour décider du mode de rendu.
 */
@Composable
private fun ParagraphBlockField(
    blockText: AnnotatedString,
    blockOffsetRange: IntRange,
    plainText: String,
    textStyle: TextStyle,
    annotations: List<Annotation>,
    chapterIndex: Int,
    highlightedRange: State<IntRange?>,
    freeSelectedRange: State<IntRange?>,
    onFreeSelectionChanged: (anchorOffset: Int, focusOffset: Int) -> Unit,
    onFreeSelectionCleared: () -> Unit,
    onFreeSelectionBoundsInWindow: (Rect?) -> Unit,
    onClick: () -> Unit,
    onAnnotationTapped: (Annotation, Rect?) -> Unit,
    isReadingRulerEnabled: Boolean,
    onCurrentLineY: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    var textLayoutResult by remember(blockOffsetRange) { mutableStateOf<TextLayoutResult?>(null) }
    var textCoordinates by remember(blockOffsetRange) { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current

    // §4.2 (audit réactivité) — lecture de `highlightedRange` + écriture de
    // `onCurrentLineY` sorties de `onTextLayout` : lire un état pendant la
    // phase de layout y abonne la mise en page (chaque mot prononcé
    // invalidait le layout de tous les blocs visibles), et écrire pendant le
    // layout déclenche une passe aval. Dérivé ici, dans un effet, à partir
    // du `TextLayoutResult` déjà capturé.
    LaunchedEffect(textLayoutResult, highlightedRange.value, isReadingRulerEnabled, blockOffsetRange) {
        if (!isReadingRulerEnabled) return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        val absRange = highlightedRange.value ?: return@LaunchedEffect
        val local = absRange.first - blockOffsetRange.first
        if (local in 0 until layout.layoutInput.text.length) {
            val line = layout.getLineForOffset(local)
            onCurrentLineY(with(density) { layout.getLineTop(line).toDp() })
        }
    }

    // Amorcée à la sélection déjà publiée : ce composable n'est monté que
    // pour le bloc qui possède `freeSelectedRange` (voir ParagraphBlockText),
    // le plus souvent parce qu'un appui long dans ParagraphBlockDisplay
    // vient de calculer un mot et de le publier — les poignées doivent
    // apparaître directement dessus, pas à `TextRange.Zero`.
    var localSelection by remember(blockOffsetRange) {
        val initial = freeSelectedRange.value?.let { g ->
            if (g.first >= blockOffsetRange.first && g.last <= blockOffsetRange.last) {
                TextRange(g.first - blockOffsetRange.first, g.last - blockOffsetRange.first + 1)
            } else {
                null
            }
        } ?: TextRange.Zero
        mutableStateOf(initial)
    }

    val globalSelection = freeSelectedRange.value
    val ownsGlobalSelection = globalSelection != null &&
        globalSelection.first >= blockOffsetRange.first &&
        globalSelection.last <= blockOffsetRange.last
    val selection = if (ownsGlobalSelection) localSelection else TextRange.Zero
    val selectionState = rememberUpdatedState(selection)

    val fieldValue = remember(blockText, selection) { TextFieldValue(blockText, selection) }

    var popupBoundsInWindow by remember(blockOffsetRange) { mutableStateOf<Rect?>(null) }
    val currentOnBoundsInWindow by rememberUpdatedState(onFreeSelectionBoundsInWindow)
    val ownsGlobalSelectionState = rememberUpdatedState(ownsGlobalSelection)

    fun hidePopup() {
        if (popupBoundsInWindow == null) return
        popupBoundsInWindow = null
        currentOnBoundsInWindow(null)
    }

    // Contrairement à PageBlock (page hors écran gardée montée par le
    // pager), un bloc scrollé hors du LazyColumn est simplement décomposé
    // — pas de LaunchedEffect(isDisplayedPage) équivalent à porter ici.

    val selectionColors = LocalTextSelectionColors.current
    val handlesOnlySelectionColors = remember(selectionColors) {
        TextSelectionColors(handleColor = selectionColors.handleColor, backgroundColor = Color.Transparent)
    }

    val toolbar = remember(blockOffsetRange) {
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
                    (blockOffsetRange.first + currentSelection.min)..(blockOffsetRange.first + currentSelection.max - 1)
                }
                val windowRect = rangeBoundsInWindow(textLayoutResult, textCoordinates, blockOffsetRange, absolute)
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
                val wasSelecting = !selection.collapsed
                val selectionChanged = newValue.selection != selection
                localSelection = newValue.selection
                if (newValue.selection.collapsed) {
                    if (wasSelecting) {
                        onFreeSelectionCleared()
                        hidePopup()
                    }
                    // Lot 23, tâche 11 — un tap qui tombe dans une
                    // annotation déjà posée ouvre son menu contextuel au
                    // lieu de basculer le HUD (comportement inchangé
                    // ailleurs dans le texte).
                    val globalOffset = blockOffsetRange.first + newValue.selection.start
                    val tappedAnnotation = annotationAtOffset(annotations, chapterIndex, globalOffset)
                    if (tappedAnnotation != null) {
                        val absolute = maxOf(tappedAnnotation.startLocator.charOffset, blockOffsetRange.first)..
                            (minOf(tappedAnnotation.endLocator.charOffset, blockOffsetRange.last + 1) - 1)
                        val bounds = rangeBoundsInWindow(textLayoutResult, textCoordinates, blockOffsetRange, absolute)
                        onAnnotationTapped(tappedAnnotation, bounds)
                    } else {
                        onClick()
                    }
                } else {
                    if (selectionChanged) hidePopup()
                    val min = newValue.selection.min
                    val max = newValue.selection.max
                    onFreeSelectionChanged(blockOffsetRange.first + min, blockOffsetRange.first + max - 1)
                }
            },
            readOnly = true,
            textStyle = textStyle,
            onTextLayout = { layout ->
                // §4.2 (audit réactivité) — capture seule du résultat : plus
                // aucune lecture d'état ni écriture pendant la phase de
                // layout (voir le `LaunchedEffect` dédié plus haut).
                textLayoutResult = layout
            },
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .onGloballyPositioned { textCoordinates = it }
                .semantics {
                    contentDescription = plainText
                    onClick(label = "Afficher ou masquer les commandes") {
                        if (!selection.collapsed) return@onClick false
                        onClick()
                        true
                    }
                }
                .drawWithCache {
                    // §4.2 (audit réactivité) — `Path` de surlignage calculé
                    // une fois par changement de plage/layout, puis mis en
                    // cache par `drawWithCache` : plus d'allocation de `Path`
                    // à chaque frame via `getPathForRange`.
                    val layout = textLayoutResult
                    val selectionPath = layout?.let { l ->
                        freeSelectedRange.value?.let { absoluteRangePath(l, blockOffsetRange, it) }
                    }
                    val wordPath = layout?.let { l ->
                        highlightedRange.value?.let { absoluteRangePath(l, blockOffsetRange, it) }
                    }
                    onDrawBehind {
                        selectionPath?.let { drawPath(it, SelectionHighlightColor) }
                        wordPath?.let { drawPath(it, WordHighlightColor) }
                    }
                },
        )
    }
}
