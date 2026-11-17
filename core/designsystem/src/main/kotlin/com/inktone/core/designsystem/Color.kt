package com.inktone.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Palette de marque "Deadly Depths" (Sous-lot 2a) — violet signature
 * InkTone, remplace le bleu legacy. Contraste vérifié WCAG 2.x sur les
 * fonds réels (#FFFBF5 / #0F1419). En sombre, `primary` est un lavande
 * clair : Material l'emploie comme couleur de texte (boutons texte, liens,
 * onglets), il doit tenir 4,5:1 sur les surfaces. Le violet profond de la
 * marque y passe en `primaryContainer`, que prennent les barres
 * (`InkToneTopBar`).
 */
val InkToneLightColorScheme = lightColorScheme(
    primary = Color(0xFF2C1E67),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DFF6),
    onPrimaryContainer = Color(0xFF19113B),
    secondary = Color(0xFFC04000),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBD1),
    onSecondaryContainer = Color(0xFF3A0A00),
    tertiary = Color(0xFF006B5E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF7FF8E3),
    onTertiaryContainer = Color(0xFF00201B),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBF5),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFF8F4EC),
    onSurface = Color(0xFF1B1B1F),
    // Rôles lus par défaut par les feuilles, cartes et menus : non définis, ils
    // retombaient sur la lavande de base Material, hors charte sur fond crème.
    surfaceDim = Color(0xFFE2DED6),
    surfaceBright = Color(0xFFFFFBF5),
    surfaceContainerLowest = Color(0xFFFFFDF9),
    surfaceContainerLow = Color(0xFFFBF7F0),
    surfaceContainer = Color(0xFFF5F1E9),
    surfaceContainerHigh = Color(0xFFEFEBE3),
    surfaceContainerHighest = Color(0xFFE9E5DD),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44464F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    inverseSurface = Color(0xFF303033),
    inverseOnSurface = Color(0xFFF2F0F4),
    inversePrimary = Color(0xFF7661D1),
    scrim = Color.Black,
)

/** Variante sombre optimisée OLED (fond quasi noir). */
val InkToneDarkColorScheme = darkColorScheme(
    primary = Color(0xFFC8BFFF),
    onPrimary = Color(0xFF2C1E67),
    primaryContainer = Color(0xFF2C1E67),
    // Blanc : le lavande (#A698E1) paraissait terne sur le violet profond.
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFFF8C5A),
    onSecondary = Color(0xFF3D0D00),
    secondaryContainer = Color(0xFF5C1A00),
    onSecondaryContainer = Color(0xFFFFDBD1),
    tertiary = Color(0xFF4DDBBF),
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF005045),
    onTertiaryContainer = Color(0xFF7FF8E3),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A202C),
    onSurface = Color(0xFFE2E2E6),
    // Même raison qu'en clair : teinte bleu-gris de la marque, pas la base Material.
    surfaceDim = Color(0xFF0F1419),
    surfaceBright = Color(0xFF353C4A),
    surfaceContainerLowest = Color(0xFF0B0F14),
    surfaceContainerLow = Color(0xFF141A24),
    surfaceContainer = Color(0xFF1A202C),
    surfaceContainerHigh = Color(0xFF232A37),
    surfaceContainerHighest = Color(0xFF2D3442),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44464F),
    inverseSurface = Color(0xFFE2E2E6),
    inverseOnSurface = Color(0xFF1B1B1F),
    inversePrimary = Color(0xFF7661D1),
    scrim = Color.Black,
)

/** Couleur associée au TTS actif (bouton, badge, indicateur). */
val ColorScheme.ttsActive: Color get() = this.secondary

/** Couleur de succès / confirmation (import, sauvegarde). */
val ColorScheme.success: Color get() = this.tertiary

/** Couleur de fond pour les cartes en surélévation légère. */
val ColorScheme.cardBackground: Color get() = this.surfaceVariant
