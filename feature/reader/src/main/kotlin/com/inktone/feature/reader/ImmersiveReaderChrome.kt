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
 */
@Composable
fun ImmersiveReaderChrome(isHudVisible: Boolean, onAutoHide: () -> Unit, content: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context as? Activity
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(isHudVisible) {
        if (isHudVisible) {
            delay(4000)
            onAutoHide()
        }
    }
    content()
}
