package com.inktone.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Palette "Signature" portée du legacy (Tâche 9bis.1.1) — bleu lecture +
 * orange TTS, seule des 4 palettes legacy retenue ici : les 3 autres
 * (Papier d'Art, Obsidian, Brouillard Nordique) n'étaient jamais
 * sélectionnables depuis les réglages (aucun `AppTheme` dans le domaine
 * actuel), portées mais jamais branchées côté UI. Ne pas réintroduire un
 * sélecteur de thème de chrome sans qu'une tâche l'exige explicitement —
 * la couleur dynamique (9bis.1.2) couvre déjà la personnalisation.
 */
val InkToneLightColorScheme = lightColorScheme(
    primary = Color(0xFF0066FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
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
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44464F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    inverseSurface = Color(0xFF303033),
    inverseOnSurface = Color(0xFFF2F0F4),
    inversePrimary = Color(0xFFAAC7FF),
    scrim = Color.Black,
)

/** Variante sombre optimisée OLED (fond quasi noir). */
val InkToneDarkColorScheme = darkColorScheme(
    primary = Color(0xFF3399FF),
    onPrimary = Color(0xFF001D3A),
    primaryContainer = Color(0xFF004A9C),
    onPrimaryContainer = Color(0xFFD6E3FF),
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
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44464F),
    inverseSurface = Color(0xFFE2E2E6),
    inverseOnSurface = Color(0xFF1B1B1F),
    inversePrimary = Color(0xFF0066FF),
    scrim = Color.Black,
)

/** Couleur associée au TTS actif (bouton, badge, indicateur). */
val ColorScheme.ttsActive: Color get() = this.secondary

/** Couleur de succès / confirmation (import, sauvegarde). */
val ColorScheme.success: Color get() = this.tertiary

/** Couleur de fond pour les cartes en surélévation légère. */
val ColorScheme.cardBackground: Color get() = this.surfaceVariant
