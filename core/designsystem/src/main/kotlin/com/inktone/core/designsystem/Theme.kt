package com.inktone.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Lot 6 — mode clair/sombre/système du chrome de l'app. Type local à
 * `core:designsystem` : ce module ne dépend pas de `domain` (Blueprint
 * §5.2/§12.4), donc pas de `com.inktone.domain.model.AppTheme` ici.
 * L'appelant (module `app`) fait la conversion depuis la préférence
 * utilisateur.
 */
enum class AppThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Tache 9bis.1.2 — couleur dynamique (Material You) avec repli. Le
 * reglage `useDynamicColor` est expose par l'appelant (branche sur les
 * reglages utilisateur en Tache 9bis.5) ; ce composable ne connait que la
 * valeur effective, pas la source du reglage.
 *
 * Lot 6 — `appTheme` pilote le mode sombre/clair/systeme de l'app,
 * distinct du theme de lecture (`ReadingTheme`) qui reste gere dans
 * feature/reader.
 *
 * Point d'attention (Blueprint) : s'applique uniquement au chrome de
 * l'app (barres, boutons, surfaces) — jamais aux themes de lecture
 * (`ReadingTheme.LIGHT/DARK/SEPIA`, choix editorial de l'utilisateur,
 * gere separement dans `feature/reader`). Ne pas melanger les deux
 * systemes de couleur.
 */
@Composable
fun InkToneTheme(
    useDynamicColor: Boolean = true,
    appTheme: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val isDark = when (appTheme) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }
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
