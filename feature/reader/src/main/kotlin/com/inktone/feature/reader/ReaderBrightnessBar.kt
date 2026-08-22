package com.inktone.feature.reader

import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/**
 * 3d.3 — Applique la luminosité choisie à la SEULE fenêtre du lecteur
 * (`WindowManager.LayoutParams.screenBrightness`), jamais au réglage
 * système : même patron que `ImmersiveReaderChrome`
 * (`LocalView.current.context as? Activity`), pas de `CompositionLocal`
 * dédié dans ce codebase pour l'Activity hôte.
 *
 * `value = null` restaure explicitement le comportement système
 * (`BRIGHTNESS_OVERRIDE_NONE`) — c'est la position "système" du réglage,
 * distincte du minimum (0.01f), sans laquelle l'utilisateur ne pourrait
 * plus revenir au comportement par défaut. `onDispose` restaure aussi
 * systématiquement, pour ne jamais laisser la luminosité forcée survivre
 * à la sortie du lecteur même si l'utilisateur quitte sans repasser par
 * "système".
 */
@Composable
fun ReaderBrightnessEffect(value: Float?) {
    val view = LocalView.current
    DisposableEffect(value) {
        val activity = view.context as? Activity
        val window = activity?.window
        if (window != null) {
            window.attributes = window.attributes.apply {
                screenBrightness = value ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
        onDispose {
            window?.let {
                it.attributes = it.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }
}

private const val MIN_BRIGHTNESS = 0.01f

/**
 * 3d.3 — Barre flottante fine, overlay au-dessus du panneau unifié (pas un
 * panneau séparé, voir `UX_FLOW_DESIGN.md` §Luminosité). `value = null`
 * signifie "valeur système" : le bouton soleil à gauche y ramène
 * explicitement, plutôt que de forcer l'utilisateur à glisser le curseur
 * jusqu'au minimum pour retrouver le comportement par défaut.
 */
@Composable
fun ReaderBrightnessBar(value: Float?, onValueChange: (Float?) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                AppSymbol.Brightness,
                contentDescription = "Valeur système",
                modifier = Modifier
                    .combinedClickable(onClick = { onValueChange(null) })
                    .background(
                        if (value == null) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Slider(
                value = value ?: MIN_BRIGHTNESS,
                onValueChange = { onValueChange(it) },
                valueRange = MIN_BRIGHTNESS..1.0f,
                steps = 0,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            AppIcon(
                AppSymbol.Brightness,
                contentDescription = "Luminosité maximale",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
