package com.inktone.core.designsystem

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView

/**
 * Peint le FOND DE FENÊTRE (`android:windowBackground`) d'un écran, restauré
 * à sa sortie.
 *
 * À ne pas confondre avec [StatusBarColorEffect], qui colore la barre de
 * statut elle-même. Ce que couvre cet effet est la bande que le fond de
 * fenêtre laisse voir là où le contenu n'est pas posé.
 *
 * ## Ce qu'il couvre depuis l'edge-to-edge
 *
 * Il a été écrit pour une bande blanche en haut du Lecteur : les barres
 * système étaient masquées mais leur inset restait consommé, si bien que le
 * contenu commençait dessous et que `themes.xml` peignait la bande en
 * `@color/brand_background` — mesuré `(255, 251, 245)` jusqu'à y=44 sur un
 * fond de page `(3, 3, 3)`.
 *
 * Cette cause a disparu avec `enableEdgeToEdge()` (targetSdk 35) : le
 * contenu remplit désormais toute la fenêtre, il ne reste aucune bande à
 * peindre. L'effet garde pourtant son utilité, plus étroite : le fond de
 * fenêtre est ce qui s'affiche AVANT la première composition. Sans lui,
 * ouvrir un livre en thème sombre montrerait brièvement le crème de
 * `windowBackground` en plein écran — le fond de la page évite ce flash.
 *
 * La couleur d'origine est capturée UNE fois ([remember]) puis restaurée au
 * départ de l'écran : sans cela, un changement de [color] restaurerait la
 * couleur précédemment posée par cet effet au lieu de celle du thème, et la
 * valeur d'origine se perdrait de proche en proche.
 */
@Composable
fun WindowBackgroundColorEffect(color: Color) {
    val view = LocalView.current
    // `LocalView` d'une preview n'est rattaché à aucune Activity.
    if (LocalInspectionMode.current) return
    val window = (view.context as? Activity)?.window ?: return
    val original = remember { window.decorView.background }
    DisposableEffect(Unit) {
        onDispose { window.setBackgroundDrawable(original) }
    }
    SideEffect {
        window.setBackgroundDrawable(ColorDrawable(color.toArgb()))
    }
}
