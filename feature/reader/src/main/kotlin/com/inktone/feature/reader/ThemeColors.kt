package com.inktone.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import com.inktone.core.designsystem.OpenDyslexicFamily
import com.inktone.core.designsystem.SourceSerifFamily
import com.inktone.core.designsystem.calculateContrastRatio
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
     * Surlignage du mot prononcé : la couleur du thème à la plus forte opacité
     * qui laisse le texte très lisible (7:1). Un jaune opaque fixe tombait à
     * 1,2:1 sous du texte blanc ; une opacité fixe basse rendait le mot
     * invisible sur fond clair. Les thèmes clairs restent donc pleins.
     */
    fun wordHighlight(theme: ReadingTheme): Color {
        val background = background(theme)
        val text = text(theme)
        val highlight = highlight(theme)
        var alpha = 1f
        while (alpha > MIN_WORD_HIGHLIGHT_ALPHA &&
            calculateContrastRatio(androidx.compose.ui.graphics.lerp(background, highlight, alpha), text) < WORD_HIGHLIGHT_MIN_CONTRAST
        ) {
            alpha -= WORD_HIGHLIGHT_ALPHA_STEP
        }
        return highlight.copy(alpha = alpha.coerceAtLeast(MIN_WORD_HIGHLIGHT_ALPHA))
    }

    internal const val WORD_HIGHLIGHT_MIN_CONTRAST = 7.0
    private const val WORD_HIGHLIGHT_ALPHA_STEP = 0.05f
    private const val MIN_WORD_HIGHLIGHT_ALPHA = 0.2f

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
     * Fond d'un élément sélectionné (chapitre en cours, puce) dans les panneaux
     * du Lecteur : teinté de l'accent, assez léger pour que le texte du thème
     * y reste lisible. Sans lui, le conteneur venait du thème de l'APP.
     */
    fun selectedContainer(theme: ReadingTheme): Color =
        androidx.compose.ui.graphics.lerp(barSurface(theme), accent(theme), SELECTED_CONTAINER_ACCENT)

    internal const val SELECTED_CONTAINER_ACCENT = 0.22f

    /**
     * Fond de la sélection de texte : l'accent du thème, translucide. Un cyan
     * fixe sortait de la charte ; l'accent reste distinct des couleurs
     * d'annotation et du surlignage TTS, qui ont chacun leur canal.
     */
    fun selectionBackground(theme: ReadingTheme): Color = accent(theme).copy(alpha = SELECTION_ALPHA)

    internal const val SELECTION_ALPHA = 0.35f

    /**
     * Icône posée sur un bouton plein de couleur [accent] (Lecture/Pause) :
     * la surface de barre tant qu'elle reste lisible (3:1, élément
     * d'interface), sinon le texte du thème.
     */
    fun onAccent(theme: ReadingTheme): Color {
        val surface = barSurface(theme)
        return if (calculateContrastRatio(accent(theme), surface) >= 3.0) surface else text(theme)
    }

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
        // Lot 21 — OpenDyslexic est embarquée (core/designsystem) et le
        // préréglage d'accessibilité l'impose : on la rend réellement, plus
        // de repli silencieux en SansSerif (écart intention↔code constaté
        // dans STRATEGIE_PERF_UX_INSPIREE_MOONREADER.md, lot 21 tâche 1).
        DomainFontFamily.OPEN_DYSLEXIC -> OpenDyslexicFamily
        // Lot 21, tâche 10 — Source Serif 4 (OFL), police de lecture
        // française à empattements.
        DomainFontFamily.SOURCE_SERIF -> SourceSerifFamily
    }
}

/**
 * Les panneaux et boîtes de dialogue du Lecteur (luminosité, réglages, voix,
 * minuteur, sommaire, signets…) reprennent les couleurs du thème de LECTURE
 * plutôt que celles de l'interface (audit UX B17). Les couleurs de surface, de
 * texte et d'accent sont dérivées comme celles des barres du HUD.
 */
@androidx.compose.runtime.Composable
internal fun ReadingThemeScope(
    theme: ReadingTheme,
    content: @androidx.compose.runtime.Composable () -> Unit,
) {
    val base = androidx.compose.material3.MaterialTheme.colorScheme
    val surface = ThemeColors.barSurface(theme)
    val text = ThemeColors.barContent(theme)
    val scheme = base.copy(
        surface = surface,
        onSurface = text,
        surfaceVariant = surface,
        onSurfaceVariant = text.copy(alpha = 0.7f),
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
        background = ThemeColors.background(theme),
        onBackground = text,
        primary = ThemeColors.accent(theme),
        onPrimary = ThemeColors.background(theme),
        primaryContainer = ThemeColors.selectedContainer(theme),
        onPrimaryContainer = text,
        secondary = ThemeColors.accent(theme),
        onSecondary = ThemeColors.background(theme),
        secondaryContainer = ThemeColors.selectedContainer(theme),
        onSecondaryContainer = text,
        tertiary = ThemeColors.accent(theme),
        onTertiary = ThemeColors.background(theme),
        outline = text.copy(alpha = 0.5f),
        outlineVariant = text.copy(alpha = 0.2f),
    )
    androidx.compose.material3.MaterialTheme(
        colorScheme = scheme,
        typography = androidx.compose.material3.MaterialTheme.typography,
        shapes = androidx.compose.material3.MaterialTheme.shapes,
        content = content,
    )
}
