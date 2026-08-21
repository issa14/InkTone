package com.inktone.feature.reader

import com.inktone.core.testing.fake.FakeChapterParser
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlaybackEvent
import com.inktone.domain.service.PlaybackPosition
import com.inktone.domain.service.TtsCapabilities
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.service.WordTimestamp
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM du surlignage par position (Lot 16, Tâche 2.3) : l'ordonnanceur
 * déduit [currentWordRange] de `AudioPlayer.playbackPosition` et signale
 * [positionValid] — position invalide → aucune plage (le ReaderViewModel
 * retombe alors sur son repli `delay()`).
 */
class PlaybackOrchestratorWordPositionTest {

    private val profile = VoiceProfile(
        id = "vp-test",
        engine = TtsEngineId.ANDROID_NATIVE,
        voice = "fr-fr",
        language = "fr-FR",
    )

    private val wordTimestamps = listOf(
        WordTimestamp(word = "Bonjour", startMs = 0, endMs = 200, charOffset = 0),
        WordTimestamp(word = "tout", startMs = 250, endMs = 450, charOffset = 8),
        WordTimestamp(word = "le", startMs = 500, endMs = 600, charOffset = 13),
    )

    private fun sentence() = Sentence(index = 0, text = "Bonjour tout le monde.", startOffset = 0, endOffset = 23)

    private suspend fun awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !condition()) {
            delay(10)
        }
        assertTrue("condition non atteinte en $timeoutMs ms", condition())
    }

    private fun position(playedMs: Long, valid: Boolean = true) = PlaybackPosition(
        playedFrame = playedMs * 22_050 / 1_000,
        sampleRate = 22_050,
        timestampNanos = null,
        valid = valid,
    )

    @Test
    fun surlignageSuitLaPositionJouee() = runBlocking {
        val player = FakeAudioPlayer()
        val orchestrator = PlaybackOrchestrator(
            ttsEngine = FakeWordTtsEngine(wordTimestamps),
            audioPlayer = player,
            updateReadingState = UpdateReadingStateUseCase(FakeReadingStateRepository()),
            chapterParser = FakeChapterParser(),
        )

        orchestrator.play(listOf(sentence()), profile, 0, "pub1", 0, "ch.xhtml")
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Playing }

        // Début de phrase : premier mot surligné, position signalée valide.
        player.emitPosition(position(playedMs = 0))
        awaitUntil { orchestrator.positionValid.value }
        awaitUntil { orchestrator.currentWordRange.value == 0 until 7 }

        // Milieu du deuxième mot.
        player.emitPosition(position(playedMs = 300))
        awaitUntil { orchestrator.currentWordRange.value == 8 until 12 }

        // Troisième mot.
        player.emitPosition(position(playedMs = 550))
        awaitUntil { orchestrator.currentWordRange.value == 13 until 15 }

        // Après le dernier mot (silence ponctué) : plus de mot actif.
        player.emitPosition(position(playedMs = 650))
        awaitUntil { orchestrator.currentWordRange.value == null }

        orchestrator.stop()
    }

    @Test
    fun positionInvalide_nEmetAucunePlage_etSignaleInvalid() = runBlocking {
        val player = FakeAudioPlayer()
        val orchestrator = PlaybackOrchestrator(
            ttsEngine = FakeWordTtsEngine(listOf(WordTimestamp(word = "x", startMs = 0, endMs = 100, charOffset = 0))),
            audioPlayer = player,
            updateReadingState = UpdateReadingStateUseCase(FakeReadingStateRepository()),
            chapterParser = FakeChapterParser(),
        )

        orchestrator.play(listOf(sentence()), profile, 0, "pub1", 0, "ch.xhtml")
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Playing }

        // Position invalide (comme à l'arrêt) : le surlignage par position
        // n'émet rien et le consommateur doit retomber sur le repli delay().
        player.emitPosition(position(playedMs = 0, valid = false))
        awaitUntil { !orchestrator.positionValid.value }
        assertNull(orchestrator.currentWordRange.value)

        orchestrator.stop()
    }

    @Test
    fun stopReinitialiseLaPlageEtLaValidite() = runBlocking {
        val player = FakeAudioPlayer()
        val orchestrator = PlaybackOrchestrator(
            ttsEngine = FakeWordTtsEngine(wordTimestamps),
            audioPlayer = player,
            updateReadingState = UpdateReadingStateUseCase(FakeReadingStateRepository()),
            chapterParser = FakeChapterParser(),
        )

        orchestrator.play(listOf(sentence()), profile, 0, "pub1", 0, "ch.xhtml")
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Playing }
        player.emitPosition(position(playedMs = 100))
        awaitUntil { orchestrator.currentWordRange.value == 0 until 7 }

        orchestrator.stop()
        assertNull(orchestrator.currentWordRange.value)
        assertEquals(false, orchestrator.positionValid.value)
    }

    // ── Fakes ──────────────────────────────────────────────

    /** Moteur qui retourne toujours un segment avec les [wordTimestamps] donnés. */
    private class FakeWordTtsEngine(private val wordTimestamps: List<WordTimestamp>) : TtsEngine {
        override val id = TtsEngineId.ANDROID_NATIVE
        override val capabilities = TtsCapabilities(
            offline = true, wordTimestamps = true, sentenceTimestamps = false,
            languages = listOf("fr-FR"), streamingSynthesis = false,
            speedControl = false, pitchControl = false, modelSizeMb = 0, license = "test",
        )

        override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment =
            AudioSegment(
                audioData = ByteArray(4),
                durationMs = 700,
                wordTimestamps = wordTimestamps,
                sampleRate = 22_050,
            )

        override fun observePlaybackEvents(): Flow<PlaybackEvent> = emptyFlow()
    }
}
