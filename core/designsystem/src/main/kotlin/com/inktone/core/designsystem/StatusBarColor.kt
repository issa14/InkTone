package com.inktone.core.designsystem

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Étend une couleur d'écran à la barre de statut Android.
 *
 * La couleur de la barre de statut était figée dans `themes.xml`
 * (`android:statusBarColor = @color/brand_background`), alors que les
 * écrans principaux peignent leur `TopAppBar` en
 * `MaterialTheme.colorScheme.primary` — une couleur qui varie avec le
 * thème d'app ET avec Material You (couleur dynamique tirée du fond
 * d'écran). Le bandeau système restait donc crème au-dessus d'une barre
 * colorée, décalage garanti sur la plupart des configurations.
 *
 * ## Contraste des icônes système
 *
 * `isAppearanceLightStatusBars` est déduit de la **luminance de la couleur
 * posée**, jamais du thème clair/sombre de l'app. C'était le second défaut
 * du réglage XML (`android:windowLightStatusBar` figé par variante) : un
 * `primary` sombre en thème clair donnait des icônes système sombres sur
 * fond sombre — invisibles. Le seuil 0.5 est celui de la luminance
 * relative WCAG, cohérent avec [ContrastRatio].
 *
 * ## Portée
 *
 * Fonctionne parce que le projet cible **targetSdk 34**. À partir de
 * targetSdk 35, Android impose l'edge-to-edge et `window.statusBarColor`
 * devient sans effet : il faudra alors dessiner soi-même derrière la barre
 * (insets), ce qui touche la mise en page de chaque écran — dette assumée
 * et documentée ici plutôt que découverte au moment du passage.
 *
 * Le Lecteur n'appelle pas cet effet : il masque entièrement les barres
 * système (`ImmersiveReaderChrome`), la couleur n'y a pas d'objet.
 */
@Composable
fun StatusBarColorEffect(color: Color) {
    val view = LocalView.current
    // `LocalView` d'une preview n'est rattaché à aucune Activity : ne rien
    // tenter plutôt que de lever une exception dans l'aperçu du studio.
    if (LocalInspectionMode.current) return
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val window = activity.window
        @Suppress("DEPRECATION") // Voir « Portée » ci-dessus (targetSdk 34).
        window.statusBarColor = color.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
            color.luminance() > LIGHT_STATUS_BAR_LUMINANCE_THRESHOLD
    }
}

/** Seuil de luminance relative (WCAG) au-dessus duquel les icônes système passent en sombre. */
private const val LIGHT_STATUS_BAR_LUMINANCE_THRESHOLD = 0.5f
