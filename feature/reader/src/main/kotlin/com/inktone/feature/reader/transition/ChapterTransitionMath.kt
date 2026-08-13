package com.inktone.feature.reader.transition

import kotlin.math.abs

/**
 * Logique pure de la transition de chapitre par résistance spatiale —
 * volontairement sans Compose ni Android pour être testable en JVM.
 *
 * Convention de signe de [accumulate] : `delta > 0` tire le contenu vers
 * le bas/droite (chapitre précédent), `delta < 0` pousse vers le
 * haut/gauche (chapitre suivant).
 */
object ChapterTransitionMath {

    /** Le contenu avance DAMPING× moins vite que le doigt (résistance). */
    const val DAMPING = 0.5f

    /** Vélocité de relâchement (px/s) qui valide la transition même sous le seuil. */
    const val MIN_FLING_VELOCITY_PX_S = 1200f

    /** Tirage maximal autorisé, en multiple du seuil (overshoot visuel). */
    const val MAX_PULL_RATIO = 1.2f

    /** Durée minimale du spinner de chargement (évite un flash si le chapitre est en cache). */
    const val MIN_LOADING_MS = 400L

    fun accumulate(pullPx: Float, delta: Float): Float = pullPx + delta * DAMPING

    fun clamp(pullPx: Float, thresholdPx: Float): Float {
        if (thresholdPx <= 0f) return 0f
        val max = thresholdPx * MAX_PULL_RATIO
        return pullPx.coerceIn(-max, max)
    }

    fun fraction(pullPx: Float, thresholdPx: Float): Float {
        if (thresholdPx <= 0f) return 0f
        return (abs(pullPx) / thresholdPx).coerceIn(0f, 1f)
    }

    fun shouldCommit(pullPx: Float, thresholdPx: Float, velocity: Float, committed: Boolean): Boolean {
        if (committed) return true
        if (thresholdPx > 0f && abs(pullPx) >= thresholdPx) return true
        return abs(velocity) >= MIN_FLING_VELOCITY_PX_S
    }
}
