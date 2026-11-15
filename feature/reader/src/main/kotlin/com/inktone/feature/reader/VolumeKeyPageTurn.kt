package com.inktone.feature.reader

import android.view.KeyEvent

/**
 * Les touches de volume tournent la page en mode paginé quand le réglage est actif
 * (audit UX D75). L'activité leur transmet les événements avant le système ; le
 * Lecteur y inscrit un gestionnaire tant qu'elles sont utiles. Passer par
 * l'activité évite de dépendre du focus de Compose, perdu à la fermeture d'un
 * panneau.
 */
object VolumeKeyPageTurn {

    /** Reçoit +1 (page suivante) ou -1 (précédente) ; `null` hors du Lecteur ou réglage inactif. */
    @Volatile
    var handler: ((Int) -> Unit)? = null

    /** Vrai si [event] est une touche de volume prise en charge : le système ne la voit alors pas. */
    fun consume(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val current = handler ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            current(if (code == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1)
        }
        return true
    }
}
