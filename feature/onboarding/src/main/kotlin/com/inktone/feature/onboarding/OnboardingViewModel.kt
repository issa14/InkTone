package com.inktone.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.service.VoiceDownloadProgress
import com.inktone.domain.service.VoiceModelDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ferme deux points laisses ouverts par les phases precedentes (Tache
 * 8.7) : le consentement crash reporting (ADR-014, jamais eu d'UI) et le
 * telechargement de voix (Tache 5.6, mecanisme fait et teste mais
 * jamais cable).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val voiceModelDownloadService: VoiceModelDownloadService,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            is OnboardingIntent.Next -> advance()
            is OnboardingIntent.SetCrashReporting -> setCrashReporting(intent.enabled)
            is OnboardingIntent.StartVoiceDownload -> startVoiceDownload()
        }
    }

    private fun advance() {
        val nextStep = when (_state.value.step) {
            OnboardingStep.Welcome -> OnboardingStep.CrashConsent
            OnboardingStep.CrashConsent -> OnboardingStep.VoiceDownload
            // Palier 1 (Android natif) reste utilisable sans telechargement
            // (ADR-018) — "Passer" ne bloque jamais la sortie d'onboarding.
            OnboardingStep.VoiceDownload -> OnboardingStep.Done
            OnboardingStep.Done -> OnboardingStep.Done
        }
        _state.value = _state.value.copy(step = nextStep)
    }

    private fun setCrashReporting(enabled: Boolean) {
        viewModelScope.launch {
            val current = preferencesRepository.get()
            preferencesRepository.update(current.copy(crashReportingEnabled = enabled))
            advance()
        }
    }

    private fun startVoiceDownload() {
        viewModelScope.launch {
            voiceModelDownloadService.downloadDefaultVoiceModel().collect { progress ->
                _state.value = _state.value.copy(downloadProgress = progress)
                if (progress is VoiceDownloadProgress.Complete || progress is VoiceDownloadProgress.Failed) {
                    advance()
                }
            }
        }
    }
}
