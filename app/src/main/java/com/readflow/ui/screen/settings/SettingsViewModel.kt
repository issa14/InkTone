package com.readflow.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readflow.data.settings.AppTheme
import com.readflow.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val voice: String = "Miro",
    val speed: Float = 1.0f,
    val gain: Float = 3.0f,
    val theme: AppTheme = AppTheme.SYSTEM,
    val dynamicColors: Boolean = false,
    val modelPath: String = "",
    val availableVoices: List<String> = listOf("Miro", "Gilles")
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { repository.voice.collect { _uiState.update { s -> s.copy(voice = it) } } }
        viewModelScope.launch { repository.speed.collect { _uiState.update { s -> s.copy(speed = it) } } }
        viewModelScope.launch { repository.gain.collect { _uiState.update { s -> s.copy(gain = it) } } }
        viewModelScope.launch { repository.theme.collect { _uiState.update { s -> s.copy(theme = it) } } }
        viewModelScope.launch { repository.dynamicColors.collect { _uiState.update { s -> s.copy(dynamicColors = it) } } }
        viewModelScope.launch { repository.modelPath.collect { _uiState.update { s -> s.copy(modelPath = it) } } }
    }

    fun setVoice(voice: String) { viewModelScope.launch { repository.setVoice(voice) } }
    fun setSpeed(speed: Float) { viewModelScope.launch { repository.setSpeed(speed) } }
    fun setGain(gain: Float) { viewModelScope.launch { repository.setGain(gain) } }
    fun setTheme(theme: AppTheme) { viewModelScope.launch { repository.setTheme(theme) } }
    fun setDynamicColors(enabled: Boolean) { viewModelScope.launch { repository.setDynamicColors(enabled) } }
    fun setModelPath(path: String) { viewModelScope.launch { repository.setModelPath(path) } }
}
