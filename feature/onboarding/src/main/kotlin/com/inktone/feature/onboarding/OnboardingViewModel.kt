package com.inktone.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lot 10 — remplace l'ancien ViewModel à deux étapes fonctionnelles
 * (consentement crash, téléchargement de voix), retirées de
 * l'onboarding (Tâche 10.3). Leur point de besoin réel vit désormais
 * ailleurs : consentement dans la carte Confidentialité des Réglages
 * (déjà câblé depuis le lot 6), voix neuronale retirée de la v1.0.0
 * (audit de consolidation, AUDIT_CONSOLIDATION_V1.md B2 — modèles non
 * distribuables dans cette version ; la voix du système est la voix de
 * la v1.0.0).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            is OnboardingIntent.Complete -> complete()
        }
    }

    private fun complete() {
        viewModelScope.launch {
            val current = preferencesRepository.get()
            preferencesRepository.update(current.copy(hasSeenOnboarding = true))
            _state.value = _state.value.copy(hasCompleted = true)
        }
    }
}
