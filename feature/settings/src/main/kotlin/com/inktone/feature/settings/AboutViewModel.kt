package com.inktone.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Expose à l'écran « À propos » (`core:ui`) le moteur TTS réellement
 * sélectionné, sous forme de libellé déjà résolu. Même contrainte que
 * [AppThemeViewModel] (Blueprint §12.4) : ni `app` ni `core:ui` ne
 * peuvent voir `PreferencesRepository`/`TtsEngineId`, la conversion se
 * fait donc ici.
 *
 * Motif : le diagnostic système copié depuis « À propos » annonçait
 * « Sherpa-ONNX (Kokoro) » en dur, quel que soit le moteur choisi — un
 * rapport de bug pouvait donc désigner le mauvais moteur.
 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
) : ViewModel() {
    val ttsEngineLabel: StateFlow<String> = preferencesRepository.observe()
        .map { engineLabel(it.defaultTtsEngine) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, engineLabel(TtsEngineId.SHERPA_ONNX))

    private companion object {
        /** Aligné sur `ttsEngineLabel` de `SettingsScreen` (Lot 14) — jamais `enum.name` brut. */
        fun engineLabel(engine: TtsEngineId): String = when (engine) {
            TtsEngineId.SHERPA_ONNX -> "Sherpa-ONNX (Kokoro)"
            TtsEngineId.ANDROID_NATIVE -> "Voix système"
            TtsEngineId.EDGE_TTS -> "Edge (cloud)"
            TtsEngineId.PIPER -> "Piper (indisponible)"
        }
    }
}
