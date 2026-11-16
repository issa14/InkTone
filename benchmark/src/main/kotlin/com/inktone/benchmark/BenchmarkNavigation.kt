package com.inktone.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

internal const val PACKAGE_NAME = "com.inktone.app"
internal const val TIMEOUT_MS = 10_000L

/**
 * Couverture d'un livre dans la bibliothèque, reconnue à la fin de son libellé
 * d'accessibilité (`BookCover` : « … % lu », « Commencé » ou « Non commencé »).
 * Jamais « le premier élément cliquable » : sur une bibliothèque vide, c'est le
 * bouton d'import, qui ouvre le sélecteur de fichiers.
 */
private val BOOK_COVER = By.desc(Pattern.compile(".*(% lu|Commencé|Non commencé)(, épinglé)?"))

/**
 * Ouvre le premier livre de la bibliothèque. Retourne `false` si aucun n'est
 * trouvé ou si le Lecteur ne remplace pas la bibliothèque : l'absence de livre
 * est un état d'appareil, pas un défaut de l'app.
 */
internal fun MacrobenchmarkScope.openFirstBook(): Boolean {
    val cover = device.wait(Until.findObject(BOOK_COVER), TIMEOUT_MS) ?: return false
    cover.click()
    return device.wait(Until.gone(BOOK_COVER), TIMEOUT_MS) == true
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
