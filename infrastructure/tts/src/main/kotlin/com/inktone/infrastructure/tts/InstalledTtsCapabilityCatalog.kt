package com.inktone.infrastructure.tts

import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.service.TtsCapabilities
import com.inktone.domain.service.TtsCapabilityCatalog
import com.inktone.domain.service.TtsEngine
import com.inktone.infrastructure.tts.di.EdgeEngine
import com.inktone.infrastructure.tts.di.Palier1
import com.inktone.infrastructure.tts.di.Palier2
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Capacités lues sur les moteurs eux-mêmes : une seule déclaration par
 * moteur, jamais recopiée ailleurs. Piper n'est pas embarqué.
 */
@Singleton
class InstalledTtsCapabilityCatalog @Inject constructor(
    @Palier2 private val sherpaOnnx: TtsEngine,
    @Palier1 private val androidNative: TtsEngine,
    @EdgeEngine private val edge: TtsEngine,
) : TtsCapabilityCatalog {

    override fun capabilitiesOf(engine: TtsEngineId): TtsCapabilities? = when (engine) {
        TtsEngineId.SHERPA_ONNX -> sherpaOnnx.capabilities
        TtsEngineId.ANDROID_NATIVE -> androidNative.capabilities
        TtsEngineId.EDGE_TTS -> edge.capabilities
        TtsEngineId.PIPER -> null
    }
}
