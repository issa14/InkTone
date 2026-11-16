package com.inktone.feature.reader.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import com.inktone.feature.reader.rangeBoundsInWindow

/**
 * Paragraphes actuellement composés en mode défilement, avec leur mise en page :
 * convertit une position fenêtre en offset absolu du chapitre et une plage
 * absolue en positions fenêtre (ADR-029). Les coordonnées sont lues au moment
 * de l'appel, donc à jour du défilement sans réécriture par image.
 */
internal class VisibleTextRegistry {

    private class Entry(val range: IntRange) {
        var layout: TextLayoutResult? = null
        var coordinates: LayoutCoordinates? = null
    }

    private val entries = LinkedHashMap<Int, Entry>()

    /** Lu par qui doit se recalculer quand un bloc arrive, part ou se remet en page. */
    var version by mutableIntStateOf(0)
        private set

    fun register(range: IntRange) {
        entries[range.first] = Entry(range)
    }

    fun unregister(range: IntRange) {
        if (entries.remove(range.first) != null) version++
    }

    fun updateLayout(range: IntRange, layout: TextLayoutResult) {
        val entry = entries[range.first] ?: return
        entry.layout = layout
        version++
    }

    fun updateCoordinates(range: IntRange, coordinates: LayoutCoordinates) {
        val entry = entries[range.first] ?: return
        if (entry.coordinates == null) version++
        entry.coordinates = coordinates
    }

    private fun Entry.isReady(): Boolean =
        layout != null && coordinates?.isAttached == true

    /** Curseur absolu (entre deux caractères) le plus proche de [window], ou null. */
    fun caretAt(window: Offset): Int? {
        val ready = entries.values.filter { it.isReady() }
        val spans = ready.map { entry ->
            val box = entry.coordinates!!.windowBox()
            BlockSpan(entry.range.first, box.top, box.bottom)
        }
        val span = nearestBlock(spans, window.y) ?: return null
        val entry = entries.getValue(span.key)
        val local = entry.coordinates!!.windowToLocal(window)
        return entry.range.first + entry.layout!!.getOffsetForPosition(local)
    }

    /**
     * Point d'ancrage fenêtre d'une poignée : bas de la ligne, à gauche du curseur
     * [caret] pour [SelectionHandle.START], à droite du dernier caractère pour END.
     * Null si le caractère visé n'est pas composé.
     */
    fun handleAnchor(caret: Int, handle: SelectionHandle): Rect? {
        val charOffset = if (handle == SelectionHandle.START) caret else caret - 1
        val entry = entries.values.firstOrNull { charOffset in it.range && it.isReady() } ?: return null
        val layout = entry.layout!!
        val local = (charOffset - entry.range.first).coerceIn(0, layout.layoutInput.text.length - 1)
        val box = layout.getBoundingBox(local)
        val x = if (handle == SelectionHandle.START) box.left else box.right
        val topLeft = entry.coordinates!!.localToWindow(Offset(x, box.top))
        return Rect(topLeft, entry.coordinates!!.localToWindow(Offset(x, box.bottom)))
    }

    /** Bornes fenêtre de la partie visible de [range] (union des blocs), ou null. */
    fun boundsInWindow(range: IntRange): Rect? =
        entries.values
            .filter { it.isReady() && it.range.first <= range.last && it.range.last >= range.first }
            .mapNotNull { rangeBoundsInWindow(it.layout, it.coordinates, it.range, range) }
            .reduceOrNull { a, b -> a.union(b) }

    private fun LayoutCoordinates.windowBox(): Rect {
        val topLeft = localToWindow(Offset.Zero)
        return Rect(topLeft, Offset(topLeft.x + size.width, topLeft.y + size.height))
    }

    private fun Rect.union(other: Rect) = Rect(
        minOf(left, other.left), minOf(top, other.top),
        maxOf(right, other.right), maxOf(bottom, other.bottom),
    )
}

/** Registre du Lecteur en mode défilement ; null ailleurs (tests, mode paginé). */
internal val LocalVisibleTextRegistry = staticCompositionLocalOf<VisibleTextRegistry?> { null }
