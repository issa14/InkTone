package com.inktone.domain.model

import org.junit.Assert.assertThrows
import org.junit.Test

class VoiceProfileTest {

    private fun validProfile(volume: Float = 1.0f) = VoiceProfile(
        id = "vp-1",
        engine = TtsEngineId.SHERPA_ONNX,
        voice = "fr_FR-upmc-medium",
        language = "fr-FR",
        volume = volume,
    )

    @Test
    fun `volume superieur a 1 est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            validProfile(volume = 1.5f)
        }
    }

    @Test
    fun `voice vide est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            validProfile().copy(voice = "")
        }
    }
}
