package com.inktone.feature.settings

import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import com.inktone.domain.model.FontFamily as DomainFontFamily

/**
 * Lot 9 — conversion police domaine → Compose, dupliquée à l'identique
 * de `feature/reader/ThemeColors.toComposeFontFamily` : un module feature
 * ne dépend jamais d'un autre module feature (Blueprint §12.3), et
 * `core/designsystem` ne peut pas dépendre de `domain` (règle
 * d'architecture vérifiée), donc aucun module commun ne peut porter cette
 * conversion pour les deux. Duplication délibérée, pas un oubli.
 */
internal fun DomainFontFamily.toComposeFontFamily(): ComposeFontFamily = when (this) {
    DomainFontFamily.DEFAULT -> ComposeFontFamily.Default
    DomainFontFamily.SERIF -> ComposeFontFamily.Serif
    DomainFontFamily.SANS_SERIF -> ComposeFontFamily.SansSerif
    DomainFontFamily.OPEN_DYSLEXIC -> ComposeFontFamily.SansSerif
}
