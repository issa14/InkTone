package com.inktone.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Tache 9bis.1.2 — couleur dynamique (Material You) avec repli. Le
 * reglage `useDynamicColor` est expose par l'appelant (branche sur les
 * reglages utilisateur en Tache 9bis.5) ; ce composable ne connait que la
 * valeur effective, pas la source du reglage.
 *
 * Point d'attention (Blueprint) : s'applique uniquement au chrome de
 * l'app (barres, boutons, surfaces) — jamais aux themes de lecture
 * (`ReadingTheme.LIGHT/DARK/SEPIA`, choix editorial de l'utilisateur,
 * gere separement dans `feature/reader`). Ne pas melanger les deux
 * systemes de couleur.
 */
@Composable
fun InkToneTheme(useDynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val colorScheme = when {
        // API 31+ uniquement - minSdk du projet est 26 (Phase 0), repli
        // obligatoire pour toutes les versions Android en dessous.
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        isDark -> InkToneDarkColorScheme
        else -> InkToneLightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = InkToneTypography, shapes = InkToneShapes, content = content)
}
