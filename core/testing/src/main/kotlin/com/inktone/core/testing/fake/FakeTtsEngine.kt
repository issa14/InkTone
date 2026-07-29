package com.inktone.core.testing.fake

import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlaybackEvent
import com.inktone.domain.service.TtsCapabilities
import com.inktone.domain.service.TtsEngine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FakeTtsEngine : TtsEngine {
    override val id = TtsEngineId.ANDROID_NATIVE
    override val capabilities = TtsCapabilities(
        offline = true, wordTimestamps = false, sentenceTimestamps = false,
        languages = listOf("fr"), streamingSynthesis = false,
        speedControl = true, pitchControl = true, modelSizeMb = 0, license = "test",
    )

    override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment =
        AudioSegment(audioData = ByteArray(0), durationMs = 0, wordTimestamps = emptyList(), sampleRate = 16000)

    override fun observePlaybackEvents(): Flow<PlaybackEvent> = callbackFlow { awaitClose { } }
}
