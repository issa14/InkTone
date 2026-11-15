package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceProfileBookLanguageTest {

    private fun profile(engine: TtsEngineId) =
        VoiceProfile(id = "vp", engine = engine, voice = "v", language = "fr-FR")

    /** Audit UX D44 : la voix système était forcée en français, quelle que soit la langue du livre. */
    @Test
    fun `la voix systeme parle la langue du livre`() {
        assertEquals("en", profile(TtsEngineId.ANDROID_NATIVE).withBookLanguage(" en ").language)
    }

    @Test
    fun `les voix propres a une langue gardent la leur`() {
        assertEquals("fr-FR", profile(TtsEngineId.SHERPA_ONNX).withBookLanguage("en").language)
        assertEquals("fr-FR", profile(TtsEngineId.EDGE_TTS).withBookLanguage("en").language)
    }

    @Test
    fun `sans langue de livre, le profil est inchange`() {
        assertEquals("fr-FR", profile(TtsEngineId.ANDROID_NATIVE).withBookLanguage(null).language)
        assertEquals("fr-FR", profile(TtsEngineId.ANDROID_NATIVE).withBookLanguage("  ").language)
    }
}
