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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

private class RecordingEngine(
    override val id: TtsEngineId,
    override val capabilities: TtsCapabilities,
    private val failWith: Exception? = null,
) : TtsEngine {
    var calls = 0
        private set

    override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment {
        calls++
        failWith?.let { throw it }
        return AudioSegment(byteArrayOf(1), durationMs = 10L, wordTimestamps = emptyList(), sampleRate = 16000)
    }

    override fun observePlaybackEvents(): Flow<PlaybackEvent> = emptyFlow()
}

class SelectiveTtsEngineTest {

    private val sentence = Sentence(index = 0, text = "Bonjour", startOffset = 0, endOffset = 7)
    private val edgeProfile = VoiceProfile(id = "vp", engine = TtsEngineId.EDGE_TTS, voice = "fr-FR-VivienneNeural", language = "fr-FR")
    private val sherpaProfile = VoiceProfile(id = "vp", engine = TtsEngineId.SHERPA_ONNX, voice = "ff_siwis", language = "fr-FR")

    private fun capabilities(id: TtsEngineId) = TtsCapabilities(
        offline = id != TtsEngineId.EDGE_TTS,
        wordTimestamps = true,
        sentenceTimestamps = true,
        languages = listOf("fr"),
        streamingSynthesis = false,
        speedControl = true,
        pitchControl = false,
        modelSizeMb = 0,
        license = "test",
    )

    @Test
    fun route_vers_edge_quand_engine_edge() = runTest {
        val edge = RecordingEngine(TtsEngineId.EDGE_TTS, capabilities(TtsEngineId.EDGE_TTS))
        val offline = RecordingEngine(TtsEngineId.SHERPA_ONNX, capabilities(TtsEngineId.SHERPA_ONNX))
        val engine = SelectiveTtsEngine(edge, offline)

        engine.synthesize(sentence, edgeProfile)

        assertEquals(1, edge.calls)
        assertEquals(0, offline.calls)
        assertEquals(TtsEngineId.EDGE_TTS, engine.id)
    }

    @Test
    fun route_vers_offline_quand_engine_sherpa() = runTest {
        val edge = RecordingEngine(TtsEngineId.EDGE_TTS, capabilities(TtsEngineId.EDGE_TTS))
        val offline = RecordingEngine(TtsEngineId.SHERPA_ONNX, capabilities(TtsEngineId.SHERPA_ONNX))
        val engine = SelectiveTtsEngine(edge, offline)

        engine.synthesize(sentence, sherpaProfile)

        assertEquals(0, edge.calls)
        assertEquals(1, offline.calls)
        assertEquals(TtsEngineId.SHERPA_ONNX, engine.id)
    }

    @Test
    fun repli_vers_offline_sur_erreur_reseau() = runTest {
        val edge = RecordingEngine(
            TtsEngineId.EDGE_TTS,
            capabilities(TtsEngineId.EDGE_TTS),
            failWith = UnknownHostException("dns"),
        )
        val offline = RecordingEngine(TtsEngineId.SHERPA_ONNX, capabilities(TtsEngineId.SHERPA_ONNX))
        val engine = SelectiveTtsEngine(edge, offline)

        engine.synthesize(sentence, edgeProfile)

        assertEquals(1, edge.calls)
        assertEquals(1, offline.calls)
        // id reflète le repli, jamais figé sur Edge
        assertEquals(TtsEngineId.SHERPA_ONNX, engine.id)
    }

    @Test
    fun erreur_permanente_est_remontee_sans_repli() = runTest {
        val edge = RecordingEngine(
            TtsEngineId.EDGE_TTS,
            capabilities(TtsEngineId.EDGE_TTS),
            failWith = IllegalStateException("403"),
        )
        val offline = RecordingEngine(TtsEngineId.SHERPA_ONNX, capabilities(TtsEngineId.SHERPA_ONNX))
        val engine = SelectiveTtsEngine(edge, offline)

        var threw = false
        try {
            engine.synthesize(sentence, edgeProfile)
        } catch (e: IllegalStateException) {
            threw = true
        }

        assertTrue("l'erreur permanente doit être remontée", threw)
        assertEquals(1, edge.calls)
        assertEquals("pas de repli silencieux sur erreur permanente", 0, offline.calls)
    }

    @Test
    fun ne_selectionne_jamais_edge_sans_engine_edge() = runTest {
        val edge = RecordingEngine(TtsEngineId.EDGE_TTS, capabilities(TtsEngineId.EDGE_TTS))
        val offline = RecordingEngine(TtsEngineId.SHERPA_ONNX, capabilities(TtsEngineId.SHERPA_ONNX))
        val engine = SelectiveTtsEngine(edge, offline)

        // ANDROID_NATIVE aussi doit router vers l'offline (jamais Edge par défaut)
        engine.synthesize(sentence, VoiceProfile(id = "vp", engine = TtsEngineId.ANDROID_NATIVE, voice = "fr-fr-default", language = "fr-FR"))

        assertEquals("Edge ne doit jamais être sélectionné implicitement", 0, edge.calls)
        assertEquals(1, offline.calls)
    }
}
