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
 * ## Le cas qui l'a rendu nécessaire
 *
 * Le Lecteur masque entièrement les barres système (`ImmersiveReaderChrome`),
 * mais l'inset de barre de statut reste consommé : le contenu commence donc
 * sous cette bande, et `themes.xml` l'y peint en `@color/brand_background`
 * (un crème `#FFFBF5`). Sur un thème de lecture sombre, cela donnait un
 * bandeau clair permanent au-dessus de la barre du haut — mesuré au pixel :
 * `(255, 251, 245)` jusqu'à y=44, puis `(3, 3, 3)` pour la page. Aucune
 * valeur de `statusBarColor` ne pouvait le corriger, la barre étant masquée.
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
