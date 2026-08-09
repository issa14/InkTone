package com.inktone.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.repository.ThemeRepository
import com.inktone.domain.usecase.DeleteCustomThemeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeGalleryViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val preferencesRepository: PreferencesRepository,
    private val deleteCustomTheme: DeleteCustomThemeUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ThemeGalleryUiState())
    val state: StateFlow<ThemeGalleryUiState> = _state.asStateFlow()

    private val _effects = Channel<ThemeGalleryEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(themeRepository.observeAll(), preferencesRepository.observe()) { all, prefs ->
                all.filterNot { it.isBuiltIn } to prefs.theme
            }.collect { (customThemes, activeThemeId) ->
                _state.value = _state.value.copy(customThemes = customThemes, activeThemeId = activeThemeId)
            }
        }
    }

    fun onIntent(intent: ThemeGalleryIntent) {
        when (intent) {
            is ThemeGalleryIntent.SelectTheme -> viewModelScope.launch {
                val current = preferencesRepository.get()
                preferencesRepository.update(current.copy(theme = intent.themeId))
            }
            is ThemeGalleryIntent.StartPreview -> _state.value = _state.value.copy(previewThemeId = intent.themeId)
            is ThemeGalleryIntent.EndPreview -> _state.value = _state.value.copy(previewThemeId = null)
            is ThemeGalleryIntent.DeleteCustomTheme -> viewModelScope.launch { deleteCustomTheme(intent.themeId) }
            is ThemeGalleryIntent.OpenStudio -> viewModelScope.launch {
                _effects.send(ThemeGalleryEffect.NavigateToStudio(intent.themeId))
            }
        }
    }
}
