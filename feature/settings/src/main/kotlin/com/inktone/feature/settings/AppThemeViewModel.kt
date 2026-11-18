package com.inktone.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.core.designsystem.AppThemeMode
import com.inktone.domain.model.AppTheme
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.repository.StartupPrefsReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Tache 9bis.1.2/9bis.5 — expose `useDynamicColor` et `appTheme` a
 * `MainActivity` (module `app`), qui n'a pas le droit de dependre de
 * `domain`/`PreferencesRepository` directement (Blueprint §12.4).
 * `InkToneTheme` s'applique avant `InkToneNavHost` dans `MainActivity`,
 * donc avant tout `hiltViewModel()` scope a une destination de nav — ce
 * ViewModel separe de `SettingsViewModel` reste minimal expres, pas
 * besoin du reste de `UserPreferences` a cet endroit.
 *
 * Valeurs initiales lues dans [StartupPrefsReader] : le premier frame
 * n'attend pas l'ouverture de Room (démarrage à froid).
 */
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    startupPrefsReader: StartupPrefsReader,
) : ViewModel() {
    val useDynamicColor: StateFlow<Boolean> = preferencesRepository.observe()
        .map { it.dynamicColorEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, startupPrefsReader.readDynamicColorEnabled())

    /**
     * Lot 6 — thème système Système/Clair/Sombre, exposé à MainActivity en
     * `AppThemeMode` (core:designsystem) : la conversion depuis `domain.AppTheme`
     * se fait ici, dans le seul module autorisé à voir les deux types (Blueprint
     * §12.4 — `app` ne peut dépendre ni de `domain` ni directement de ce mapping).
     */
    val appTheme: StateFlow<AppThemeMode> = preferencesRepository.observe()
        .map { it.appTheme.toMode() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, startupPrefsReader.readAppTheme().toMode())

    /**
     * Lot 10 — pilote le `startDestination` de `InkToneNavHost` depuis
     * `MainActivity` (même contrainte Blueprint §12.4 qu'au-dessus :
     * `app` ne voit jamais `PreferencesRepository`/`UserPreferences`
     * directement). `null` = valeur encore inconnue (première installation,
     * cache de démarrage vide) — distinct de `false`, pour que `MainActivity`
     * garde le splash plutôt que de risquer un flash sur `LibraryRoute`
     * avant de rediriger vers l'onboarding.
     */
    val hasSeenOnboarding: StateFlow<Boolean?> = preferencesRepository.observe()
        .map { it.hasSeenOnboarding }
        .stateIn(viewModelScope, SharingStarted.Eagerly, startupPrefsReader.readHasSeenOnboarding())

    private fun AppTheme.toMode(): AppThemeMode = when (this) {
        AppTheme.SYSTEM -> AppThemeMode.SYSTEM
        AppTheme.LIGHT -> AppThemeMode.LIGHT
        AppTheme.DARK -> AppThemeMode.DARK
    }
}
