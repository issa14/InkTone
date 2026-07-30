package com.inktone.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Tache 9bis.1.2/9bis.5 — expose uniquement `useDynamicColor` a
 * `MainActivity` (module `app`), qui n'a pas le droit de dependre de
 * `domain`/`PreferencesRepository` directement (Blueprint §12.4).
 * `InkToneTheme` s'applique avant `InkToneNavHost` dans `MainActivity`,
 * donc avant tout `hiltViewModel()` scope a une destination de nav — ce
 * ViewModel separe de `SettingsViewModel` reste minimal expres, pas
 * besoin du reste de `UserPreferences` a cet endroit.
 */
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
) : ViewModel() {
    val useDynamicColor: StateFlow<Boolean> = preferencesRepository.observe()
        .map { it.dynamicColorEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
}
