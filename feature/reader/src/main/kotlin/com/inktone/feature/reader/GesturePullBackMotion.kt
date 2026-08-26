package com.inktone.feature.reader

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import com.inktone.core.designsystem.Motion

/**
 * Lot 21 — spec du retour élastique du geste de tirage de chapitre
 * (mode paginé et mode défilement).
 *
 * Le rebond était un `spring(...)` écrit en dur, qui ignorait la
 * réduction de mouvement : un ressort « rapide » reste un rebond, donc
 * exactement ce que le réglage demande d'éviter. La réduction de
 * mouvement a DEUX sources :
 * - la préférence applicative `reduceMotion` (UserPreferences), gérée
 *   directement ici ;
 * - le réglage système Android (échelle d'animation), délégué en entier à
 *   [Motion.gestureSpring], qui se neutralise déjà par construction —
 *   correctif Lot 21 : cette fonction recalculait auparavant le même
 *   test système en double au lieu de le laisser à [Motion.gestureSpring].
 */
@Composable
internal fun gesturePullBackSpec(reduceMotion: Boolean): FiniteAnimationSpec<Float> =
    if (reduceMotion) tween(durationMillis = 0) else Motion.gestureSpring()
