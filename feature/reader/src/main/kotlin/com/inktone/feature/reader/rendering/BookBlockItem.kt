package com.inktone.feature.reader.rendering

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setSelection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.BookBlock
import com.inktone.domain.service.EpubResourceResolver
import com.inktone.feature.reader.SelectionHighlightColor
import com.inktone.feature.reader.absoluteRangePath
import com.inktone.feature.reader.annotationSpanStyle
import com.inktone.feature.reader.rangeBoundsInWindow
import com.inktone.feature.reader.selection.LocalVisibleTextRegistry
import com.inktone.feature.reader.selection.VisibleTextRegistry
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
 * 3. Appliquer le surlignage dans le `Text` de CE bloc uniquement.
 *
 * Cette recherche est faite UNE FOIS par `ReaderScreen` (offset absolu →
 * `IntRange` absolu, cf. `absoluteHighlightedRange` du mode SCROLL) ; ce
 * composable ne fait ensuite qu'une soustraction locale par bloc,
 * exactement comme `PageBlock` (mode PAGED) le fait par page.
 *
 * ## Sélection libre + popup d'action
 *
 * Tous les paragraphes rendent par `Text` ([ParagraphBlockText]) : la
 * sélection est une plage absolue du chapitre, dessinée dans chaque bloc
 * qu'elle touche. Fonctions partagées avec `PageBlock` (`PagedChapterContent.kt`) :
 * [rangeBoundsInWindow], [absoluteRangePath], [SelectionHighlightColor].
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
    /** Couleur du mot prononcé, dérivée du thème : `ThemeColors.wordHighlight`. */
    wordHighlightColor: Color,
    freeSelectedRange: State<IntRange?> = rememberUpdatedState(null),
    onFreeSelectionChanged: (anchorOffset: Int, focusOffset: Int) -> Unit = { _, _ -> },
    onFreeSelectionCleared: () -> Unit = {},
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
                wordHighlightColor = wordHighlightColor,
                freeSelectedRange = freeSelectedRange,
                onFreeSelectionChanged = onFreeSelectionChanged,
                onFreeSelectionCleared = onFreeSelectionCleared,
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
 * Rendu par `Text` d'un [BookBlock.ParagraphBlock] : surlignage mot-à-mot TTS,
 * sélection libre, tap sur lien ou annotation.
 *
 * La sélection est dessinée depuis la plage absolue du chapitre, qu'elle tienne
 * ou non dans ce bloc ; un appui long la démarre au mot. Aucun `BasicTextField`,
 * coûteux au défilement (audit réactivité §4.1) : poignées, loupe et popup sont
 * portés par le Lecteur à partir du [VisibleTextRegistry] (ADR-029).
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
    wordHighlightColor: Color,
    freeSelectedRange: State<IntRange?>,
    onFreeSelectionChanged: (anchorOffset: Int, focusOffset: Int) -> Unit,
    onFreeSelectionCleared: () -> Unit,
    onClick: () -> Unit,
    onAnnotationTapped: (Annotation, Rect?) -> Unit,
    isReadingRulerEnabled: Boolean,
    onCurrentLineY: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onLinkTapped = LocalLinkTapHandler.current
    var textLayoutResult by remember(blockOffsetRange) { mutableStateOf<TextLayoutResult?>(null) }
    var textCoordinates by remember(blockOffsetRange) { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current
    val registry = LocalVisibleTextRegistry.current
    if (registry != null) {
        DisposableEffect(registry, blockOffsetRange) {
            registry.register(blockOffsetRange)
            onDispose { registry.unregister(blockOffsetRange) }
        }
    }

    // Le mot prononcé est COLLECTÉ (`snapshotFlow`), jamais lu en clé d'effet :
    // lu pendant la composition, il recomposait ce bloc à chaque mot (audit
    // réactivité §4.2). Même raison pour la sortie de `onTextLayout`.
    LaunchedEffect(textLayoutResult, isReadingRulerEnabled, blockOffsetRange) {
        if (!isReadingRulerEnabled) return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        snapshotFlow { highlightedRange.value }.collect { absRange ->
            if (absRange == null) return@collect
            val local = absRange.first - blockOffsetRange.first
            if (local in 0 until layout.layoutInput.text.length) {
                val line = layout.getLineForOffset(local)
                onCurrentLineY(with(density) { layout.getLineTop(line).toDp() })
            }
        }
    }

    Text(
        text = blockText,
        style = textStyle,
        onTextLayout = { layout ->
            textLayoutResult = layout
            registry?.updateLayout(blockOffsetRange, layout)
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .onGloballyPositioned {
                textCoordinates = it
                registry?.updateCoordinates(blockOffsetRange, it)
            }
            .semantics {
                contentDescription = plainText
                onClick(label = "Afficher ou masquer les commandes") {
                    onClick()
                    true
                }
                setSelection(label = "Sélectionner") { start, end, _ ->
                    if (start == end) return@setSelection false
                    onFreeSelectionChanged(
                        blockOffsetRange.first + minOf(start, end),
                        blockOffsetRange.first + maxOf(start, end) - 1,
                    )
                    true
                }
            }
            .pointerInput(blockOffsetRange) {
                detectTapGestures(
                    onTap = { position ->
                        // Un tap n'importe où dans le texte ferme la sélection en
                        // cours (le popup ne se ferme pas sur un tap extérieur).
                        if (freeSelectedRange.value != null) {
                            onFreeSelectionCleared()
                            return@detectTapGestures
                        }
                        val layout = textLayoutResult ?: run {
                            onClick()
                            return@detectTapGestures
                        }
                        val localOffset = layout.getOffsetForPosition(position)
                        val link = blockText.linkAt(localOffset)
                        if (link != null) {
                            onLinkTapped(link)
                            return@detectTapGestures
                        }
                        val globalOffset = blockOffsetRange.first + localOffset
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
                        val boundary = layout.getWordBoundary(layout.getOffsetForPosition(position))
                        if (boundary.collapsed) return@detectTapGestures
                        onFreeSelectionChanged(
                            blockOffsetRange.first + boundary.start,
                            blockOffsetRange.first + boundary.end - 1,
                        )
                    },
                )
            }
            .drawWithCache {
                // `Path` recalculé seulement quand la plage ou le layout changent (§4.2).
                val layout = textLayoutResult
                val selectionPath = layout?.let { l ->
                    freeSelectedRange.value?.let { absoluteRangePath(l, blockOffsetRange, it) }
                }
                val wordPath = layout?.let { l ->
                    highlightedRange.value?.let { absoluteRangePath(l, blockOffsetRange, it) }
                }
                onDrawBehind {
                    selectionPath?.let { drawPath(it, SelectionHighlightColor) }
                    wordPath?.let { drawPath(it, wordHighlightColor) }
                }
            },
    )
}
