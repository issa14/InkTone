package com.inktone.core.designsystem

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Accorde le contraste des icônes système à la couleur dessinée DERRIÈRE
 * elles.
 *
 * En edge-to-edge (`enableEdgeToEdge`, `MainActivity`), les barres système
 * sont transparentes et leur couleur vient du contenu : une `TopAppBar`
 * peint son fond jusqu'au bord haut de l'écran. Il ne reste donc plus rien
 * à colorer — mais tout à contraster, et c'est ce que fait cet effet.
 *
 * La luminance de [backgroundColor] décide seule, jamais le mode
 * clair/sombre. Ni celui du système (que `SystemBarStyle.auto` consulterait,
 * alors que le thème d'InkTone peut le contredire —
 * `AppThemeMode.LIGHT`/`DARK`), ni celui de l'app : une `TopAppBar` en
 * `colorScheme.primary` est sombre y compris en thème CLAIR, et des icônes
 * sombres y seraient invisibles. C'était le défaut de
 * `android:windowLightStatusBar`, figé par variante de ressources.
 *
 * Le seuil 0.5 est celui de la luminance relative WCAG, cohérent avec
 * [ContrastRatio].
 */
@Composable
fun SystemBarIconsEffect(backgroundColor: Color) {
    val view = LocalView.current
    // `LocalView` d'une preview n'est rattaché à aucune Activity.
    if (LocalInspectionMode.current) return
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val isLight = backgroundColor.luminance() > LIGHT_SYSTEM_BAR_LUMINANCE_THRESHOLD
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = isLight
            isAppearanceLightNavigationBars = isLight
        }
    }
}

/** Seuil de luminance relative (WCAG) au-dessus duquel les icônes système passent en sombre. */
private const val LIGHT_SYSTEM_BAR_LUMINANCE_THRESHOLD = 0.5f
