package com.inktone.feature.settings

import com.inktone.domain.model.ReadingTheme

/**
 * Lot 9, Tâche 9.4 — état de la Galerie de thèmes (UX §Galerie de
 * thèmes). [ambiances]/[accessibility] sont fixes (le catalogue intégré
 * ne change jamais à l'exécution) ; seuls [customThemes] et
 * [activeThemeId] varient, observés en continu (`ThemeRepository`/
 * `PreferencesRepository`).
 *
 * Plus d'aperçu par appui long (audit UI P2-15) : geste caché qu'un texte
 * permanent devait annoncer. Chaque carte montre déjà une page dans le
 * thème, et un toucher l'applique, réversible d'un autre toucher.
 */
data class ThemeGalleryUiState(
    val ambiances: List<ReadingTheme> = ReadingTheme.AMBIANCES,
    val accessibility: List<ReadingTheme> = ReadingTheme.ACCESSIBILITY,
    val customThemes: List<ReadingTheme> = emptyList(),
    val activeThemeId: String = ReadingTheme.DEFAULT.id,
)

sealed interface ThemeGalleryIntent {
    data class SelectTheme(val themeId: String) : ThemeGalleryIntent
    data class DeleteCustomTheme(val themeId: String) : ThemeGalleryIntent
    data class OpenStudio(val themeId: String?) : ThemeGalleryIntent
}

sealed interface ThemeGalleryEffect {
    /** `themeId` null = création (carte pointillée), non-null = édition (icône crayon). */
    data class NavigateToStudio(val themeId: String?) : ThemeGalleryEffect
}
