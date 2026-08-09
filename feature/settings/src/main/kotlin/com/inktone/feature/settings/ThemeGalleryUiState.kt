package com.inktone.feature.settings

import com.inktone.domain.model.ReadingTheme

/**
 * Lot 9, Tâche 9.4 — état de la Galerie de thèmes (UX §Galerie de
 * thèmes). [ambiances]/[accessibility] sont fixes (le catalogue intégré
 * ne change jamais à l'exécution) ; seuls [customThemes] et
 * [activeThemeId] varient, observés en continu (`ThemeRepository`/
 * `PreferencesRepository`).
 *
 * [previewThemeId] porte l'appui long en cours (Tâche 9.4 — « Appui long
 * pour tester ») : un état d'aperçu ponctuel, jamais persisté, distinct
 * de [activeThemeId]. Écart déclaré (contrat point 5) — la
 * prévisualisation est un recouvrement plein écran DANS la Galerie
 * (mockup de page agrandi), pas une poussée en direct dans un Reader
 * déjà ouvert ailleurs : `ReaderViewModel` ne réobserve pas en continu
 * `UserPreferences.theme` (seulement à l'ouverture/`SetOverrides`), et
 * ajouter cette réobservation aurait débordé largement le périmètre de
 * ce lot pour un gain marginal (aucun Reader n'est visible pendant qu'on
 * parcourt la Galerie).
 */
data class ThemeGalleryUiState(
    val ambiances: List<ReadingTheme> = ReadingTheme.AMBIANCES,
    val accessibility: List<ReadingTheme> = ReadingTheme.ACCESSIBILITY,
    val customThemes: List<ReadingTheme> = emptyList(),
    val activeThemeId: String = ReadingTheme.DEFAULT.id,
    val previewThemeId: String? = null,
) {
    /** Ce que la carte doit afficher comme "ACTIF" — la prévisualisation prime visuellement, sans jamais être persistée. */
    val displayedActiveId: String get() = previewThemeId ?: activeThemeId
    val previewedTheme: ReadingTheme?
        get() = previewThemeId?.let { id -> (ambiances + accessibility + customThemes).firstOrNull { it.id == id } }
}

sealed interface ThemeGalleryIntent {
    data class SelectTheme(val themeId: String) : ThemeGalleryIntent
    data class StartPreview(val themeId: String) : ThemeGalleryIntent
    data object EndPreview : ThemeGalleryIntent
    data class DeleteCustomTheme(val themeId: String) : ThemeGalleryIntent
    data class OpenStudio(val themeId: String?) : ThemeGalleryIntent
}

sealed interface ThemeGalleryEffect {
    /** `themeId` null = création (carte pointillée), non-null = édition (icône crayon). */
    data class NavigateToStudio(val themeId: String?) : ThemeGalleryEffect
}
