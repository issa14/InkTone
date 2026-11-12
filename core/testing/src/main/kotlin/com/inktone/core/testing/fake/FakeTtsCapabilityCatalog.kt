package com.inktone.core.testing.fake

import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.service.TtsCapabilities
import com.inktone.domain.service.TtsCapabilityCatalog

/** Catalogue de test : seuls les moteurs de [pitchEngines] déclarent l'intonation. */
class FakeTtsCapabilityCatalog(
    private val pitchEngines: Set<TtsEngineId> = setOf(TtsEngineId.ANDROID_NATIVE, TtsEngineId.EDGE_TTS),
) : TtsCapabilityCatalog {

    override fun capabilitiesOf(engine: TtsEngineId): TtsCapabilities = TtsCapabilities(
        offline = engine != TtsEngineId.EDGE_TTS, wordTimestamps = true, sentenceTimestamps = true,
        languages = listOf("fr"), streamingSynthesis = false, speedControl = true,
        pitchControl = engine in pitchEngines, modelSizeMb = 0, license = "test",
    )
}
