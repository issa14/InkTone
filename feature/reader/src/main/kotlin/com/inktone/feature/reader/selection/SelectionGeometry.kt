package com.inktone.feature.reader.selection

/** Extrémité de la sélection libre que porte une poignée (ADR-029). */
internal enum class SelectionHandle { START, END }

/**
 * Résultat du déplacement d'une poignée : la nouvelle plage absolue (bornes
 * incluses, comme `ReaderUiState.freeSelectionRange`) et la poignée que le doigt
 * tient désormais — elle change de rôle quand elle croise l'autre.
 */
internal data class HandleDrag(val range: IntRange, val dragged: SelectionHandle)

/**
 * Déplace la poignée [dragged] au curseur [caret] (position entre deux
 * caractères, offset absolu), l'autre extrémité de [range] restant fixe.
 * Une plage vide est refusée : la sélection garde au moins un caractère.
 */
internal fun dragHandle(range: IntRange, dragged: SelectionHandle, caret: Int): HandleDrag {
    val startCaret = range.first
    val endCaret = range.last + 1
    return when (dragged) {
        SelectionHandle.START -> when {
            caret < endCaret -> HandleDrag(caret until endCaret, SelectionHandle.START)
            caret > endCaret -> HandleDrag(endCaret until caret, SelectionHandle.END)
            else -> HandleDrag(range, dragged)
        }
        SelectionHandle.END -> when {
            caret > startCaret -> HandleDrag(startCaret until caret, SelectionHandle.END)
            caret < startCaret -> HandleDrag(caret until startCaret, SelectionHandle.START)
            else -> HandleDrag(range, dragged)
        }
    }
}

/** Étendue verticale (fenêtre) d'un bloc visible, pour [nearestBlock]. */
internal data class BlockSpan(val key: Int, val top: Float, val bottom: Float)

/**
 * Bloc visé par une ordonnée fenêtre : celui qui la contient, sinon le plus
 * proche (le doigt entre deux paragraphes, ou au-dessus du premier visible).
 */
internal fun nearestBlock(spans: List<BlockSpan>, y: Float): BlockSpan? =
    spans.minByOrNull { span ->
        when {
            y < span.top -> span.top - y
            y > span.bottom -> y - span.bottom
            else -> 0f
        }
    }

/**
 * Décalage de défilement automatique pour une image, quand le doigt qui tient une
 * poignée est à moins de [edge] du bord haut ou bas de la zone de lecture
 * ([top]..[bottom]) : proportionnel à la profondeur dans la marge, au plus
 * [maxStep]. Négatif vers le haut, nul hors des marges.
 */
internal fun autoScrollStep(y: Float, top: Float, bottom: Float, edge: Float, maxStep: Float): Float {
    if (edge <= 0f) return 0f
    return when {
        y < top + edge -> -maxStep * ((top + edge - y) / edge).coerceAtMost(1f)
        y > bottom - edge -> maxStep * ((y - (bottom - edge)) / edge).coerceAtMost(1f)
        else -> 0f
    }
}
