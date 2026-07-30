package com.inktone.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.usecase.ApplyAccessibilityPresetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MVI standard (Tache 8.1) — chaque intent appelle
 * `preferencesRepository.update(current.copy(...))`. Rien de nouveau
 * cote domaine ici, uniquement le branchement UI (Phase 2, Tache 1.6/2.6
 * deja fonctionnel).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val applyAccessibilityPreset: ApplyAccessibilityPresetUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.observe().collect { preferences ->
                _state.value = _state.value.copy(preferences = preferences)
            }
        }
    }

    fun onIntent(intent: SettingsIntent) {
        val current = _state.value.preferences
        viewModelScope.launch {
            when (intent) {
                is SettingsIntent.SetTheme -> preferencesRepository.update(current.copy(theme = intent.theme))
                is SettingsIntent.SetFontSize -> preferencesRepository.update(current.copy(fontSize = intent.fontSize))
                is SettingsIntent.SetFontFamily -> preferencesRepository.update(current.copy(fontFamily = intent.fontFamily))
                is SettingsIntent.SetDefaultTtsEngine ->
                    preferencesRepository.update(current.copy(defaultTtsEngine = intent.engine))
                is SettingsIntent.SetLanguage -> preferencesRepository.update(current.copy(language = intent.language))
                is SettingsIntent.SetCrashReportingEnabled ->
                    preferencesRepository.update(current.copy(crashReportingEnabled = intent.enabled))
                is SettingsIntent.SetReduceMotion -> preferencesRepository.update(current.copy(reduceMotion = intent.enabled))
                is SettingsIntent.SetDynamicColorEnabled ->
                    preferencesRepository.update(current.copy(dynamicColorEnabled = intent.enabled))
                is SettingsIntent.SetReadingRulerEnabled ->
                    preferencesRepository.update(current.copy(readingRulerEnabled = intent.enabled))
                is SettingsIntent.ApplyAccessibilityPreset -> applyAccessibilityPreset()
            }
        }
    }
}
