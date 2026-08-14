package com.inktone.feature.reader.transition

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ChapterTransitionDirection { PREVIOUS, NEXT }

/**
 * État local du geste de transition de chapitre — jamais hissé dans
 * [com.inktone.feature.reader.ReaderUiState] (MVI : état transitoire du
 * doigt), la navigation réelle reste `ReaderIntent.Next/PreviousChapter`.
 */
@Stable
class ChapterTransitionState {

    private var _pullPx by mutableFloatStateOf(0f)
    private var _direction by mutableStateOf<ChapterTransitionDirection?>(null)
    private var _isDragging by mutableStateOf(false)
    private var _isLoading by mutableStateOf(false)
    private var _committed by mutableStateOf(false)
    private var _targetChapterIndex by mutableIntStateOf(-1)

    /** Seuil de validation (25 % de la dimension de lecture), fourni par l'appelant. */
    var thresholdPx by mutableFloatStateOf(0f)

    /** Tirage amorti courant (signé : >0 = précédent, <0 = suivant). */
    val pullPx: Float get() = _pullPx

    val direction: ChapterTransitionDirection? get() = _direction

    val isDragging: Boolean get() = _isDragging

    val isLoading: Boolean get() = _isLoading

    val targetChapterIndex: Int get() = _targetChapterIndex

    val fraction: Float
        get() = ChapterTransitionMath.fraction(pullPx, thresholdPx)

    fun onDrag(delta: Float, newDirection: ChapterTransitionDirection) {
        _direction = newDirection
        _isDragging = true
        _pullPx = ChapterTransitionMath.clamp(
            ChapterTransitionMath.accumulate(_pullPx, delta),
            thresholdPx,
        )
        // Hystérésis : une fois le seuil franchi, on reste « validé » même
        // si le doigt relâche légèrement la traction (comportement premium).
        if (fraction >= 1f) _committed = true
    }

    /** true → valider la transition ; false → annuler (rebond élastique). */
    fun resolveRelease(velocity: Float): Boolean =
        ChapterTransitionMath.shouldCommit(_pullPx, thresholdPx, velocity, _committed)

    fun cancel() {
        _isDragging = false
        _direction = null
        _pullPx = 0f
        _committed = false
    }

    fun beginLoading(targetChapterIndex: Int) {
        _isDragging = false
        _isLoading = true
        _committed = false
        _targetChapterIndex = targetChapterIndex
    }

    fun finish() {
        _pullPx = 0f
        _direction = null
        _isLoading = false
        _committed = false
        _targetChapterIndex = -1
    }
}
