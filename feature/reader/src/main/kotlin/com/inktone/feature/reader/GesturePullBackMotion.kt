package com.inktone.feature.reader

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import com.inktone.core.designsystem.Motion
import com.inktone.core.designsystem.reducedMotionDuration

/**
 * Lot 21 — spec du retour élastique du geste de tirage de chapitre
 * (mode paginé et mode défilement).
 *
 * Le rebond était un `spring(...)` écrit en dur, qui ignorait la
 * réduction de mouvement : un ressort « rapide » reste un rebond, donc
 * exactement ce que le réglage demande d'éviter. La réduction de
 * mouvement a DEUX sources, toutes deux respectées ici :
 * - la préférence applicative `reduceMotion` (UserPreferences) ;
 * - le réglage système Android (échelle d'animation), via
 *   [Motion.gestureSpring] qui se neutralise par construction.
 */

/**
 * Décide si le retour du geste de tirage doit être élastique (spring) ou
 * instantané. Pure, testable en JVM : `true` seulement si AUCUNE
 * réduction de mouvement n'est active (préférence applicative ou réglage
 * système).
 */
internal fun pullBackIsElastic(reduceMotion: Boolean, systemMotionReduced: Boolean): Boolean =
    !reduceMotion && !systemMotionReduced

/** Spec concrète du retour, résolue dans la composition. */
@Composable
internal fun gesturePullBackSpec(reduceMotion: Boolean): FiniteAnimationSpec<Float> {
    val systemMotionReduced = reducedMotionDuration(Motion.DURATION_STANDARD_MS) == 0
    return if (pullBackIsElastic(reduceMotion, systemMotionReduced)) {
        Motion.gestureSpring()
    } else {
        tween(durationMillis = 0)
    }
}
