package com.inktone.feature.reader

/**
 * Lot 21, tâche 9 — auto-scroll visuel en mode SCROLL.
 *
 * Vitesse par cran, exprimée en dp/s (le rendu convertit en px via la
 * densité, seule couche à la connaître). `0` = désactivé. Pure et
 * testable en JVM.
 */
internal fun autoScrollDpPerSecond(speed: Int): Float = when (speed) {
    1 -> 30f
    2 -> 60f
    3 -> 120f
    else -> 0f
}
