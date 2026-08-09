package com.inktone.feature.settings

import com.inktone.core.designsystem.calculateContrastRatio
import com.inktone.core.designsystem.toColor

/**
 * Lot 9, Tâche 9.5 — état du Studio de thème. Les quatre couleurs restent
 * TOUJOURS des hex `#RRGGBB` valides : le sélecteur de couleur (dialogue
 * dédié, écran) ne pousse une valeur ici qu'une fois validée, jamais une
 * saisie intermédiaire invalide — [contrastRatio] peut donc parser sans
 * garde supplémentaire.
 *
 * [editingThemeId] null = création (ouverte depuis la carte pointillée) ;
 * non-null = édition d'un thème personnalisé existant (icône crayon).
 */
data class ThemeStudioUiState(
    val editingThemeId: String? = null,
    val name: String = "",
    val backgroundColorHex: String = "#FFFFFF",
    val textColorHex: String = "#000000",
    val accentColorHex: String = "#1976D2",
    val highlightColorHex: String = "#FFEB3B",
) {
    /** Badge WCAG en direct (Tâche 9.5) — informatif, ne bloque jamais Sauvegarder. */
    val contrastRatio: Double
        get() = calculateContrastRatio(backgroundColorHex.toColor(), textColorHex.toColor())

    val wcagLevel: WcagLevel
        get() = when {
            contrastRatio >= 7.0 -> WcagLevel.AAA
            contrastRatio >= 4.5 -> WcagLevel.AA
            else -> WcagLevel.BELOW_THRESHOLD
        }
}

enum class WcagLevel { AAA, AA, BELOW_THRESHOLD }

enum class StarterPalette { SOMBRE, CLAIR, CHAUD, NEON }

sealed interface ThemeStudioIntent {
    data class Load(val themeId: String?) : ThemeStudioIntent
    data class SetName(val name: String) : ThemeStudioIntent
    data class SetBackgroundColor(val hex: String) : ThemeStudioIntent
    data class SetTextColor(val hex: String) : ThemeStudioIntent
    data class SetAccentColor(val hex: String) : ThemeStudioIntent
    data class SetHighlightColor(val hex: String) : ThemeStudioIntent
    data class ApplyStarterPalette(val palette: StarterPalette) : ThemeStudioIntent
    data object Save : ThemeStudioIntent
    data object ConfirmDelete : ThemeStudioIntent
}

sealed interface ThemeStudioEffect {
    data object SavedAndClose : ThemeStudioEffect
    data object DeletedAndClose : ThemeStudioEffect
}
