package com.inktone.feature.reader.selection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Poignée en cours de glissement : laquelle, où est le doigt et où viser le texte
 * (fenêtre). [fromFinger] décale la visée du doigt, posé sous la poignée, vers le
 * milieu de la ligne.
 */
private class ActiveDrag(var handle: SelectionHandle, var finger: Offset, val fromFinger: Offset) {
    val target: Offset get() = finger + fromFinger
}

/**
 * Poignées de la sélection libre en mode défilement, en surimpression de la liste
 * (ADR-029) : glissement au caractère d'un paragraphe à l'autre, loupe sur le
 * texte visé, défilement automatique près des bords. À placer dans la même `Box`
 * que la `LazyColumn`, après elle, à la même taille.
 *
 * @param onDraggingChanged vrai pendant un glissement : le popup d'actions se masque.
 */
@Composable
internal fun SelectionHandlesOverlay(
    registry: VisibleTextRegistry,
    listState: LazyListState,
    selection: IntRange?,
    onSelectionChanged: (anchorOffset: Int, focusOffset: Int) -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val handleColor = LocalTextSelectionColors.current.handleColor
    val currentSelection by rememberUpdatedState(selection)
    val currentOnSelectionChanged by rememberUpdatedState(onSelectionChanged)
    val currentOnDraggingChanged by rememberUpdatedState(onDraggingChanged)

    val overlay = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var drag by remember { mutableStateOf<ActiveDrag?>(null) }
    // Incrémenté à chaque déplacement du doigt : la loupe et les poignées le lisent.
    var dragTick by remember { mutableIntStateOf(0) }

    fun applyTarget(active: ActiveDrag) {
        val range = currentSelection ?: return
        val caret = registry.caretAt(active.target) ?: return
        val result = dragHandle(range, active.handle, caret)
        active.handle = result.dragged
        if (result.range != range) {
            // L'ancre est l'extrémité fixe : le focus suit la poignée tenue.
            val (anchor, focus) = if (result.dragged == SelectionHandle.START) {
                result.range.last to result.range.first
            } else {
                result.range.first to result.range.last
            }
            currentOnSelectionChanged(anchor, focus)
        }
    }

    // Défilement automatique tant qu'une poignée est tenue près d'un bord.
    val active = drag
    if (active != null) {
        LaunchedEffect(active) {
            val edge = with(density) { 56.dp.toPx() }
            val maxStep = with(density) { 14.dp.toPx() }
            while (true) {
                withFrameNanos { }
                val coords = overlay[0]?.takeIf { it.isAttached } ?: continue
                val top = coords.localToWindow(Offset.Zero).y
                val step = autoScrollStep(active.finger.y, top, top + coords.size.height, edge, maxStep)
                if (step != 0f) {
                    listState.scrollBy(step)
                    applyTarget(active)
                    dragTick++
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlay[0] = it }
            .magnifier(
                sourceCenter = {
                    dragTick
                    val current = drag
                    val coords = overlay[0]
                    if (current == null || coords == null || !coords.isAttached) {
                        Offset.Unspecified
                    } else {
                        coords.windowToLocal(current.target)
                    }
                },
            ),
    ) {
        val range = selection ?: return@Box
        for (handle in SelectionHandle.entries) {
            val caret = if (handle == SelectionHandle.START) range.first else range.last + 1
            SelectionHandleKnob(
                handle = handle,
                color = handleColor,
                anchor = {
                    // Relu à chaque placement : suit le défilement sans recomposer.
                    registry.version
                    listState.firstVisibleItemScrollOffset
                    dragTick
                    val coords = overlay[0]?.takeIf { it.isAttached }
                    registry.handleAnchor(caret, handle)?.let { rect ->
                        coords?.let { Rect(it.windowToLocal(rect.topLeft), it.windowToLocal(rect.bottomRight)) }
                    }
                },
                onDragStart = { fingerWindow ->
                    val anchor = registry.handleAnchor(caret, handle) ?: return@SelectionHandleKnob
                    val target = Offset(anchor.left, anchor.center.y)
                    drag = ActiveDrag(handle, fingerWindow, fromFinger = target - fingerWindow)
                    currentOnDraggingChanged(true)
                },
                onDrag = { fingerWindow ->
                    val current = drag ?: return@SelectionHandleKnob
                    current.finger = fingerWindow
                    applyTarget(current)
                    dragTick++
                },
                onDragEnd = {
                    drag = null
                    currentOnDraggingChanged(false)
                },
            )
        }
    }
}

/**
 * Une poignée : goutte dessinée sous l'extrémité de la sélection, pointe vers le
 * texte, dans une cible tactile de 48 dp. [anchor] donne le segment vertical du
 * curseur (coordonnées de la surimpression), relu au placement.
 */
@Composable
private fun SelectionHandleKnob(
    handle: SelectionHandle,
    color: Color,
    anchor: () -> Rect?,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val density = LocalDensity.current
    val touch = 48.dp
    val radius = with(density) { 11.dp.toPx() }
    val touchPx = with(density) { touch.toPx() }
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // `pointerInput(handle)` garde son premier bloc : les rappels doivent rester à jour.
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Box(
        modifier = Modifier
            .offset {
                val rect = anchor()
                if (rect == null) {
                    // Extrémité hors écran : la poignée aussi.
                    IntOffset(-10_000, -10_000)
                } else {
                    // Le cercle pend sous la ligne, côté extérieur de la sélection.
                    val circleX = if (handle == SelectionHandle.START) rect.left - radius else rect.left + radius
                    val circleY = rect.bottom + radius
                    IntOffset((circleX - touchPx / 2).roundToInt(), (circleY - touchPx / 2).roundToInt())
                }
            }
            .size(touch)
            .onGloballyPositioned { coords[0] = it }
            .semantics { invisibleToUser() }
            .pointerInput(handle) {
                detectDragGestures(
                    onDragStart = { local -> coords[0]?.let { currentOnDragStart(it.localToWindow(local)) } },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() },
                    onDrag = { change, _ ->
                        change.consume()
                        coords[0]?.let { currentOnDrag(it.localToWindow(change.position)) }
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(color, radius, center)
            // Coin carré tourné vers le texte : la pointe de la goutte.
            val cornerLeft = if (handle == SelectionHandle.START) center.x else center.x - radius
            drawRect(color, Offset(cornerLeft, center.y - radius), Size(radius, radius))
        }
    }
}
