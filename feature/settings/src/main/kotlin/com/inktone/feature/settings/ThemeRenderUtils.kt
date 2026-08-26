package com.inktone.feature.settings

import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import com.inktone.core.designsystem.OpenDyslexicFamily
import com.inktone.core.designsystem.SourceSerifFamily
import com.inktone.domain.model.FontFamily as DomainFontFamily

/**
 * Lot 9 — conversion police domaine → Compose, dupliquée à l'identique
 * de `feature/reader/ThemeColors.toComposeFontFamily` : un module feature
 * ne dépend jamais d'un autre module feature (Blueprint §12.3), et
 * `core/designsystem` ne peut pas dépendre de `domain` (règle
 * d'architecture vérifiée), donc aucun module commun ne peut porter cette
 * conversion pour les deux. Duplication délibérée, pas un oubli.
 *
 * Lot 21 — OpenDyslexic est rendue réellement (police embarquée), plus de
 * repli SansSerif : la galerie de thèmes ne doit pas mentir sur le rendu.
 */
internal fun DomainFontFamily.toComposeFontFamily(): ComposeFontFamily = when (this) {
    DomainFontFamily.DEFAULT -> ComposeFontFamily.Default
    DomainFontFamily.SERIF -> ComposeFontFamily.Serif
    DomainFontFamily.SANS_SERIF -> ComposeFontFamily.SansSerif
    DomainFontFamily.OPEN_DYSLEXIC -> OpenDyslexicFamily
    // Lot 21, tâche 10 — Source Serif 4 (OFL), même famille que le Lecteur.
    DomainFontFamily.SOURCE_SERIF -> SourceSerifFamily
}
