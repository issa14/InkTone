package com.inktone.feature.reader

import com.inktone.core.designsystem.InkToneSpacing

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
import com.inktone.core.designsystem.InkToneLevelBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.view.View
import kotlinx.coroutines.delay

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
 * Réglage de luminosité du lecteur, rangé dans le panneau « Réglages du
 * texte » (audit UI P1-6 : une barre flottante de plus dans le HUD pour un
 * réglage rare). `value = null` signifie "valeur système" : « Auto » y
 * ramène, plutôt que de forcer l'utilisateur à glisser jusqu'au minimum.
 */
@Composable
fun ReaderBrightnessControl(value: Float?, onValueChange: (Float?) -> Unit, modifier: Modifier = Modifier) {
    // État local pendant le geste : l'écran et la barre bougent aussitôt, la base
    // n'est écrite qu'une fois le geste posé (audit UX B16). Chaque échantillon
    // faisait une lecture et une écriture en base, et recomposait tout l'écran.
    var live by remember(value) { mutableStateOf(value) }
    val view = LocalView.current
    val systemBrightness = remember(view) { readSystemBrightness(view) }
    LaunchedEffect(live) {
        applyWindowBrightness(view, live)
        if (live != value) {
            delay(BRIGHTNESS_PERSIST_DELAY_MS)
            onValueChange(live)
        }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Retour au réglage du système : un libellé « Auto », distinct du soleil
        // de droite (luminosité maximale) qui lui ressemblait trait pour trait.
        Text(
            "Auto",
            style = MaterialTheme.typography.labelLarge,
            color = if (live == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .semantics { contentDescription = "Luminosité du système" }
                .combinedClickable(onClick = { live = null })
                .background(
                    if (live == null) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = InkToneSpacing.sm, vertical = 6.dp),
        )
        Spacer(Modifier.width(InkToneSpacing.sm))
        // Rail sans pouce plutôt qu'un curseur : ce réglage se manipule au
        // pouce, en pleine page et souvent dans le noir — une cible pleine
        // largeur se trouve sans regarder, et le chiffre exact n'apprend
        // rien au lecteur, seul le résultat à l'écran compte.
        InkToneLevelBar(
            // En mode « Auto », la barre montre la luminosité réelle du système plutôt que d'être vide.
            value = live ?: systemBrightness,
            // Le plancher est conservé : à zéro l'écran serait noir, et
            // l'utilisateur n'aurait plus de quoi viser pour remonter.
            onValueChange = { live = it.coerceAtLeast(MIN_BRIGHTNESS) },
            contentDescription = "Luminosité",
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(InkToneSpacing.sm))
        AppIcon(
            AppSymbol.Brightness,
            contentDescription = "Luminosité maximale",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Luminosité courante du système (0 à 1), lecture seule, sans permission ; repli à mi-course. */
private fun readSystemBrightness(view: View): Float =
    runCatching {
        android.provider.Settings.System.getInt(view.context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f).coerceIn(MIN_BRIGHTNESS, 1f)

/** Délai sans geste avant d'écrire la luminosité en base. */
private const val BRIGHTNESS_PERSIST_DELAY_MS = 250L

private fun applyWindowBrightness(view: View, value: Float?) {
    val window = (view.context as? Activity)?.window ?: return
    window.attributes = window.attributes.apply {
        screenBrightness = value ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
}
