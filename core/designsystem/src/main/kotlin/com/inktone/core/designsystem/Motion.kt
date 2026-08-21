package com.inktone.core.designsystem

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

/**
 * P5 (plan polissage Pareto) — durées et courbes de mouvement de l'app.
 *
 * Les durées étaient jusqu'ici écrites en dur dans chaque écran
 * (`tween(fadeDuration)`, ressorts locaux). Deux conséquences : le rythme
 * variait d'un écran à l'autre sans intention, et chaque site devait penser à
 * passer par [reducedMotionDuration] — ce qu'un site oublie tôt ou tard.
 *
 * Ces fabriques y passent **par construction** : une animation construite ici
 * respecte le réglage système d'échelle d'animation sans que l'appelant ait à
 * s'en souvenir. C'est la seule façon de rendre l'accessibilité du mouvement
 * fiable ailleurs que dans les intentions.
 *
 * Trois durées seulement, et c'est volontaire : une palette plus fine ne se
 * distingue pas à l'usage et se transforme vite en valeurs choisies au hasard.
 */
object Motion {

    /** Réaction immédiate à un geste : état de pression, bascule d'icône. */
    const val DURATION_FAST_MS = 120

    /** Transition standard : apparition du HUD, changement de panneau. */
    const val DURATION_STANDARD_MS = 240

    /** Mouvement ample : feuille modale, transition d'écran. */
    const val DURATION_SLOW_MS = 400

    /** Entrées et sorties courantes — décélération naturelle. */
    val StandardEasing = FastOutSlowInEasing

    /** Éléments qui entrent dans l'écran : démarrage franc, arrivée douce. */
    val EnterEasing = LinearOutSlowInEasing

    /**
     * Animation de durée [durationMs], neutralisée si le système demande de
     * réduire les animations.
     */
    @Composable
    fun <T> tween(
        durationMs: Int = DURATION_STANDARD_MS,
        easing: androidx.compose.animation.core.Easing = StandardEasing,
    ): FiniteAnimationSpec<T> = tween(durationMillis = reducedMotionDuration(durationMs), easing = easing)

    /**
     * Ressort de l'app pour les mouvements pilotés au doigt (tirage de
     * chapitre, retour élastique).
     *
     * Un ressort n'a pas de durée à annuler : quand le mouvement est réduit,
     * on renvoie une animation de durée nulle plutôt qu'un ressort « rapide »,
     * qui resterait un rebond — exactement ce que le réglage demande d'éviter.
     */
    @Composable
    fun <T> gestureSpring(
        dampingRatio: Float = Spring.DampingRatioMediumBouncy,
        stiffness: Float = Spring.StiffnessMediumLow,
    ): FiniteAnimationSpec<T> =
        if (reducedMotionDuration(DURATION_STANDARD_MS) == 0) {
            tween(durationMillis = 0)
        } else {
            spring(dampingRatio = dampingRatio, stiffness = stiffness)
        }
}
