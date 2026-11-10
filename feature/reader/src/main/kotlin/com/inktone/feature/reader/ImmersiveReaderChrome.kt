package com.inktone.feature.reader

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

/**
 * Tache 9bis.3.1 — mode immersif : masque les barres systeme pendant la
 * lecture (contenu plein ecran), les fait reapparaitre temporairement
 * sur balayage depuis le bord (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`).
 * Porte quasi tel quel du legacy — deja la bonne solution ici, rien a
 * ameliorer (voir plan Phase 9bis §9bis.3.1).
 *
 * `onAutoHide` : appele apres 4s quand [isHudVisible] devient vrai —
 * l'appelant decide comment repasser `isHudVisible` a faux (ReaderScreen
 * pilote son propre etat, ce composable ne fait que le delai).
 *
 * [immersive] : faux dès que le Lecteur cesse d'être la destination
 * courante (retour à la Bibliothèque, ouverture des Réglages), donc au
 * DÉBUT de la transition de navigation. Lier le masquage à la seule durée
 * de vie du composable restaurait les barres à la FIN de la transition :
 * l'écran d'arrivée, mis en page sans barre de statut pendant tout le
 * fondu, sautait alors d'un cran vers le bas une fois l'animation finie.
 */
@Composable
fun ImmersiveReaderChrome(
    isHudVisible: Boolean,
    onAutoHide: () -> Unit,
    hudActivityTick: Int = 0,
    immersive: Boolean = true,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    DisposableEffect(immersive) {
        if (!immersive) return@DisposableEffect onDispose {}
        val activity = view.context as? Activity
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    // hudActivityTick (bug réel trouvé à l'audit) : sans lui, ce délai ne
    // redémarre jamais tant qu'isHudVisible reste vrai — une interaction
    // avec le HUD au bout de 3s ne l'empêchait pas de se masquer une
    // seconde plus tard, sous les doigts de l'utilisateur.
    LaunchedEffect(isHudVisible, hudActivityTick) {
        if (isHudVisible) {
            delay(4000)
            onAutoHide()
        }
    }
    content()
}
