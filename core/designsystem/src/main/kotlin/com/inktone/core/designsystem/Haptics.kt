package com.inktone.core.designsystem

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * P5 (plan polissage Pareto) — échelle haptique **sémantique** de l'app.
 *
 * ## Pourquoi ne pas utiliser `LocalHapticFeedback` de Compose
 *
 * Sur la version de Compose de ce projet (BOM 2024.09.02, UI 1.7),
 * `HapticFeedbackType` ne comporte que `LongPress` et `TextHandleMove` : les
 * deux seuls retours disponibles sont donc beaucoup trop appuyés pour un
 * changement de page, et il n'existe aucune distinction confirmation/refus.
 * Les constantes de la plateforme ([HapticFeedbackConstants]) portent en
 * revanche la sémantique réelle, et le système les traduit dans le vocabulaire
 * du vibreur de l'appareil — c'est ce qui distingue une app qui « vibre » d'une
 * app dont on sent les actions.
 *
 * ## Dégradation par version
 *
 * `CONFIRM`/`REJECT` existent depuis Android 11 et `SEGMENT_TICK` depuis
 * Android 14 ; en dessous, chaque appel retombe sur le retour le plus proche
 * disponible plutôt que de ne rien faire. Jamais de simulation par vibration
 * brute : un retour haptique fabriqué à la main ne respecterait ni le réglage
 * système de l'utilisateur, ni le vocabulaire de son appareil (K « un moteur ne
 * fait jamais semblant »).
 *
 * Aucun appel ne force la vibration : [View.performHapticFeedback] respecte le
 * réglage d'accessibilité du système, qui reste seul décisionnaire.
 */
class AppHaptics internal constructor(private val view: View) {

    /**
     * Franchissement discret : page tournée, cran de réglage atteint. Le plus
     * léger de l'échelle — il se répète beaucoup, il doit rester imperceptible
     * autrement que comme une texture.
     */
    fun tick() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(constant)
    }

    /** Action aboutie : signet posé, annotation enregistrée. */
    fun confirm() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(constant)
    }

    /**
     * Limite atteinte : fin du livre, geste sans effet. Le seul retour de
     * l'échelle qui dit « non » — à ne jamais employer pour une action réussie,
     * sous peine de brouiller les deux.
     */
    fun reject() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }

    /** Appui long reconnu, avant que l'action ne se déclenche. */
    fun longPress() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

/** Échelle haptique liée à la vue courante. */
@Composable
fun rememberAppHaptics(): AppHaptics {
    val view = LocalView.current
    return remember(view) { AppHaptics(view) }
}
