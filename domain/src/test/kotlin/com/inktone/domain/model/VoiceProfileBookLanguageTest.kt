package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `une voix propre a une langue avertit pour un livre d'une autre langue`() {
        assertTrue(profile(TtsEngineId.SHERPA_ONNX).readsOtherLanguageThan("en"))
        assertTrue(profile(TtsEngineId.EDGE_TTS).readsOtherLanguageThan("en-GB"))
    }

    @Test
    fun `pas d'avertissement pour la meme langue, la voix systeme ou une langue inconnue`() {
        assertFalse(profile(TtsEngineId.EDGE_TTS).readsOtherLanguageThan("fr"))
        assertFalse(profile(TtsEngineId.EDGE_TTS).readsOtherLanguageThan("FR_ca"))
        assertFalse(profile(TtsEngineId.ANDROID_NATIVE).readsOtherLanguageThan("en"))
        assertFalse(profile(TtsEngineId.SHERPA_ONNX).readsOtherLanguageThan(null))
        assertFalse(profile(TtsEngineId.SHERPA_ONNX).readsOtherLanguageThan(" "))
    }
}
