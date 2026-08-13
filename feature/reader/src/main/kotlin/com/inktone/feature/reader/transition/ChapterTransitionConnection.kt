package com.inktone.feature.reader.transition

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Connexion de scroll imbriqué unique pour les deux orientations : capte
 * l'overscroll au bord du chapitre, amortit le tirage (via
 * [ChapterTransitionState]) et décide au relâchement (distance, vélocité
 * ou hystérésis) s'il faut valider ou annuler.
 */
class ChapterTransitionConnection(
    private val state: ChapterTransitionState,
    private val orientation: Orientation,
    private val canPullPrevious: () -> Boolean,
    private val canPullNext: () -> Boolean,
    private val onCommit: (ChapterTransitionDirection) -> Unit,
    private val onCancel: () -> Unit,
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // UserInput = drag utilisateur (Drag est déprécié en 1.7 au profit de UserInput).
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        val delta = if (orientation == Orientation.Vertical) available.y else available.x
        // Signe scroll : delta < 0 = tirer vers le bas/droite (chapitre
        // précédent), delta > 0 = pousser vers le haut/gauche (suivant). On
        // inverse pour que `pullPx` soit positif côté « précédent ».
        return when {
            delta < 0f && canPullPrevious() -> {
                state.onDrag(-delta, ChapterTransitionDirection.PREVIOUS)
                consume(delta)
            }
            delta > 0f && canPullNext() -> {
                state.onDrag(-delta, ChapterTransitionDirection.NEXT)
                consume(delta)
            }
            else -> Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val direction = state.direction ?: return available
        val velocity = if (orientation == Orientation.Vertical) available.y else available.x
        return if (state.resolveRelease(velocity)) {
            onCommit(direction)
            Velocity.Zero
        } else {
            onCancel()
            Velocity.Zero
        }
    }

    private fun consume(delta: Float): Offset =
        if (orientation == Orientation.Vertical) Offset(0f, delta) else Offset(delta, 0f)
}
