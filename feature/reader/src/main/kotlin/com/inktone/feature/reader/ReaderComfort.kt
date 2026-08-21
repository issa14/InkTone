package com.inktone.feature.reader

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inktone.domain.model.UserPreferences

/**
 * P4 (plan polissage Pareto) — réglages de confort de lecture visuelle qui
 * touchent la fenêtre ou la géométrie de page, hors du panneau lui-même.
 */

/**
 * Convertit un cran de marge en valeur concrète.
 *
 * Trois crans plutôt qu'un curseur continu : l'utilisateur choisit un confort,
 * pas une mesure. `NORMAL` reprend exactement l'ancienne valeur en dur (16 dp),
 * pour qu'une bibliothèque existante s'ouvre sans changement visible.
 *
 * Le résultat alimente une seule variable côté `ReaderScreen`, consommée à la
 * fois par la mesure de pagination et par le rendu — jamais deux valeurs
 * distinctes, qui feraient déborder le texte hors de la page mesurée.
 */
fun readerMarginFor(step: Int): Dp = when (step.coerceIn(UserPreferences.MARGIN_STEP_RANGE)) {
    0 -> 8.dp
    2 -> 32.dp
    else -> 16.dp
}

/** Libellé du cran de marge, pour le panneau de réglages. */
fun readerMarginLabel(step: Int): String = when (step.coerceIn(UserPreferences.MARGIN_STEP_RANGE)) {
    0 -> "Étroites"
    2 -> "Larges"
    else -> "Normales"
}

/**
 * Maintient l'écran allumé pendant la lecture visuelle.
 *
 * `DisposableEffect` plutôt qu'un drapeau posé une fois : le maintien doit
 * disparaître avec l'écran de lecture, sinon il survivrait à la fermeture du
 * Lecteur et viderait la batterie sur un autre écran de l'app — le genre de
 * fuite qu'un simple `addFlags` sans retrait produit systématiquement.
 *
 * Sans effet sur la narration écran éteint : celle-ci ne dépend pas de
 * l'écran, elle est portée par le service de lecture (P1).
 */
@Composable
fun KeepScreenOnEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = (view.context as? Activity)?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
