package com.inktone.infrastructure.tts

import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlaybackEvent
import com.inktone.domain.service.TtsCapabilities
import com.inktone.domain.service.TtsEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTtsEngine(
    override val id: TtsEngineId,
    override val capabilities: TtsCapabilities,
    private val shouldFail: Boolean = false,
) : TtsEngine {
    var callCount = 0
        private set

    override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment {
        callCount++
        if (shouldFail) throw IllegalStateException("echec simule")
        return AudioSegment(audioData = byteArrayOf(1, 2, 3), durationMs = 10L, wordTimestamps = emptyList(), sampleRate = 16000)
    }

    override fun observePlaybackEvents(): Flow<PlaybackEvent> = emptyFlow()
}

class FallbackTtsEngineTest {

    private val sentence = Sentence(index = 0, text = "Bonjour", startOffset = 0, endOffset = 7)
    private val voiceProfile = VoiceProfile(id = "vp", engine = TtsEngineId.SHERPA_ONNX, voice = "v", language = "fr-FR")

    private fun capabilities(wordTimestamps: Boolean) = TtsCapabilities(
        offline = true, wordTimestamps = wordTimestamps, sentenceTimestamps = true,
        languages = listOf("fr"), streamingSynthesis = false, speedControl = true,
        pitchControl = false, modelSizeMb = 0, license = "test",
    )

    @Test
    fun utilise_le_palier_2_tant_qu_il_ne_signale_aucun_echec() = runTest {
        val primary = FakeTtsEngine(TtsEngineId.SHERPA_ONNX, capabilities(wordTimestamps = false))
        val fallback = FakeTtsEngine(TtsEngineId.ANDROID_NATIVE, capabilities(wordTimestamps = true))
        val engine = FallbackTtsEngine(primary, fallback)

        engine.synthesize(sentence, voiceProfile)

        assertEquals(1, primary.callCount)
        assertEquals(0, fallback.callCount)
        assertEquals(TtsEngineId.SHERPA_ONNX, engine.id)
        assertFalse("capabilities doit refleter le Palier 2 actif", engine.capabilities.wordTimestamps)
    }

    @Test
    fun bascule_vers_le_palier_1_quand_le_palier_2_echoue() = runTest {
        val primary = FakeTtsEngine(TtsEngineId.SHERPA_ONNX, capabilities(wordTimestamps = false), shouldFail = true)
        val fallback = FakeTtsEngine(TtsEngineId.ANDROID_NATIVE, capabilities(wordTimestamps = true))
        val engine = FallbackTtsEngine(primary, fallback)

        val segment = engine.synthesize(sentence, voiceProfile)

        assertEquals(1, primary.callCount)
        assertEquals(1, fallback.callCount)
        assertTrue(segment.audioData.isNotEmpty())
        assertEquals(
            "id doit refleter le moteur reellement actif apres repli, jamais fige sur le Palier 2",
            TtsEngineId.ANDROID_NATIVE,
            engine.id,
        )
        assertTrue(
            "capabilities doit refleter le Palier 1 actif apres repli - jamais pretendre wordTimestamps=false une fois replie sur un moteur qui les fournit",
            engine.capabilities.wordTimestamps,
        )
    }

    @Test
    fun reste_sur_le_palier_1_pour_les_appels_suivants_apres_un_premier_echec() = runTest {
        val primary = FakeTtsEngine(TtsEngineId.SHERPA_ONNX, capabilities(wordTimestamps = false), shouldFail = true)
        val fallback = FakeTtsEngine(TtsEngineId.ANDROID_NATIVE, capabilities(wordTimestamps = true))
        val engine = FallbackTtsEngine(primary, fallback)

        engine.synthesize(sentence, voiceProfile)
        engine.synthesize(sentence, voiceProfile)

        assertEquals("le Palier 2 ne doit pas etre retente une fois le repli declenche", 1, primary.callCount)
        assertEquals(2, fallback.callCount)
    }
}
