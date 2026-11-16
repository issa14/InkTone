package com.inktone.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val PACKAGE_NAME = "com.inktone.app"
internal const val TIMEOUT_MS = 10_000L

/**
 * Ouvre le premier livre de la bibliothèque. Retourne `false` si aucun n'est
 * trouvé : l'absence de livre est un état d'appareil, pas un défaut de l'app.
 */
internal fun MacrobenchmarkScope.openFirstBook(): Boolean {
    val candidate = device.findObject(By.clickable(true).longClickable(true))
        ?: device.findObject(By.clickable(true))
        ?: return false
    candidate.click()
    return device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), TIMEOUT_MS)
}

/**
 * Balayage vertical de lecture (pas un lancer), au centre de l'écran : même
 * geste que `scripts/measure-reader-frames.sh scroll`, pour que les deux
 * mesures restent comparables. [down] = vers la suite du texte.
 */
internal fun MacrobenchmarkScope.readingSwipe(down: Boolean) {
    val x = device.displayWidth / 2
    val low = device.displayHeight * 70 / 100
    val high = device.displayHeight * 30 / 100
    // ~300 ms : uiautomator compte 5 ms par pas.
    if (down) device.swipe(x, low, x, high, 60) else device.swipe(x, high, x, low, 60)
}
