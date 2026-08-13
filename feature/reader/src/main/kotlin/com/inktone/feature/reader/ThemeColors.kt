package com.inktone.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import com.inktone.core.designsystem.toColor
import com.inktone.domain.model.EffectiveReadingSettings
import com.inktone.domain.model.FontFamily as DomainFontFamily
import com.inktone.domain.model.ReadingTheme

/**
 * Extrait de ReaderScreen (Tâche 4.7) pour rester testable en JVM pur —
 * un Composable privé ne peut pas être appelé depuis un test JUnit
 * classique sans moteur de rendu Compose.
 *
 * Lot 9 — les couleurs viennent désormais du [ReadingTheme] résolu
 * (intégré ou personnalisé), plus d'un `when` exhaustif sur un enum fermé.
 */
object ThemeColors {
    fun background(theme: ReadingTheme): Color = theme.backgroundColorHex.toColor()
    fun text(theme: ReadingTheme): Color = theme.textColorHex.toColor()
    fun accent(theme: ReadingTheme): Color = theme.accentColorHex.toColor()
    fun highlight(theme: ReadingTheme): Color = theme.highlightColorHex.toColor()

    /**
     * Couleur des barres HUD (topbar, panneau de contrôle), dérivée du
     * thème de LECTURE actif plutôt que du thème CHROME de l'app
     * (`MaterialTheme.colorScheme.surface`).
     *
     * Bug réel rapporté sur appareil : les deux thèmes sont volontairement
     * découplés (voir Theme.kt), mais `colorScheme.surface` peut se
     * retrouver proche en luminosité du fond de page en dessous quand le
     * thème de lecture choisi diverge du mode chrome — la barre paraît
     * alors « presque transparente ». En dérivant la couleur de la barre
     * du fond de lecture lui-même (mélangé vers la couleur de texte du
     * même thème, qui est déjà garantie lisible dessus), le contraste
     * reste correct quel que soit le thème choisi, par construction.
     */
    fun barSurface(theme: ReadingTheme): Color =
        androidx.compose.ui.graphics.lerp(background(theme), text(theme), 0.10f)

    /** Couleur de contenu (texte, icônes) des barres HUD — voir [barSurface]. */
    fun barContent(theme: ReadingTheme): Color = text(theme)

    /**
     * Police effectivement rendue (Tâche 9.2) : la préférence globale
     * explicite gagne quand elle est définie (ex. OpenDyslexic imposé par
     * le préréglage d'accessibilité) ; `FontFamily.DEFAULT` signifie
     * « pas de choix explicite », et c'est alors la police du thème actif
     * qui s'applique — c'est CE choix qui doit entrer dans
     * `PaginationStyleKey.fontFamilyKey` (via le `TextStyle` réellement
     * rendu, jamais la police en dur) pour que changer d'ambiance
     * recalcule la pagination quand elle change de famille, sans que les
     * couleurs (qui n'affectent jamais `TextStyle`) n'invalident quoi que
     * ce soit.
     */
    fun effectiveFontFamily(effectiveSettings: EffectiveReadingSettings, theme: ReadingTheme): DomainFontFamily =
        if (effectiveSettings.fontFamily != DomainFontFamily.DEFAULT) effectiveSettings.fontFamily else theme.fontFamily

    fun toComposeFontFamily(fontFamily: DomainFontFamily): ComposeFontFamily = when (fontFamily) {
        DomainFontFamily.DEFAULT -> ComposeFontFamily.Default
        DomainFontFamily.SERIF -> ComposeFontFamily.Serif
        DomainFontFamily.SANS_SERIF -> ComposeFontFamily.SansSerif
        // OpenDyslexic n'est pas une police système Android — sans-serif
        // en repli tant qu'aucune police embarquée n'est fournie (hors
        // périmètre de ce lot, signalé plutôt que simulé silencieusement,
        // K12 : jamais de compensation aval d'un problème amont).
        DomainFontFamily.OPEN_DYSLEXIC -> ComposeFontFamily.SansSerif
    }
}
