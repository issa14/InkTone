package com.inktone.feature.reader

import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeTtsSegmentCache
import com.inktone.core.testing.fake.FakePronunciationRuleRepository
import com.inktone.core.testing.fake.FakeVoiceProfileRepository
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.ChapterParser
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit UX D39 — après une pause, la phrase affichée et la position
 * enregistrée ne doivent pas passer devant le son. Test en temps réel :
 * la charge de la machine ne peut que rallonger l'écart mesuré, jamais le
 * faire passer à tort.
 */
class PlaybackOrchestratorPauseTest {

    private class TimedTtsEngine(private val durationMs: Long) : com.inktone.domain.service.TtsEngine {
        override val id = TtsEngineId.ANDROID_NATIVE
        override val capabilities = com.inktone.domain.service.TtsCapabilities(
            offline = true, wordTimestamps = false, sentenceTimestamps = false, languages = listOf("fr-FR"),
            streamingSynthesis = false, speedControl = false, pitchControl = false, modelSizeMb = 0, license = "test",
        )

        override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile) =
            com.inktone.domain.service.AudioSegment(
                audioData = ByteArray(4), durationMs = durationMs, wordTimestamps = emptyList(), sampleRate = 22_050,
            )

        override fun observePlaybackEvents(): kotlinx.coroutines.flow.Flow<com.inktone.domain.service.PlaybackEvent> =
            kotlinx.coroutines.flow.emptyFlow()
    }

    /** Synthèse qui ne se termine jamais : la session reste en Buffering. */
    private class HangingTtsEngine : com.inktone.domain.service.TtsEngine {
        override val id = TtsEngineId.ANDROID_NATIVE
        override val capabilities = com.inktone.domain.service.TtsCapabilities(
            offline = true, wordTimestamps = false, sentenceTimestamps = false, languages = listOf("fr-FR"),
            streamingSynthesis = false, speedControl = false, pitchControl = false, modelSizeMb = 0, license = "test",
        )

        override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): com.inktone.domain.service.AudioSegment =
            kotlinx.coroutines.awaitCancellation()

        override fun observePlaybackEvents(): kotlinx.coroutines.flow.Flow<com.inktone.domain.service.PlaybackEvent> =
            kotlinx.coroutines.flow.emptyFlow()
    }

    private fun orchestrator(engine: com.inktone.domain.service.TtsEngine, audioPlayer: FakeAudioPlayer = FakeAudioPlayer()) =
        PlaybackOrchestrator(
            ttsEngine = engine,
            audioPlayer = audioPlayer,
            updateReadingState = UpdateReadingStateUseCase(FakeReadingStateRepository()),
            getReadingState = GetReadingStateUseCase(FakeReadingStateRepository()),
            chapterParser = com.inktone.core.testing.fake.FakeChapterParser(),
            publicationRepository = FakePublicationRepository(),
            publicationParser = FakePublicationParser(),
            preferencesRepository = FakePreferencesRepository(),
            voiceProfileRepository = FakeVoiceProfileRepository(),
            ttsSegmentCache = FakeTtsSegmentCache(),
            pronunciationRuleRepository = FakePronunciationRuleRepository(),
        )

    private val profile = VoiceProfile(id = "vp", engine = TtsEngineId.ANDROID_NATIVE, voice = "v", language = "fr-FR")

    private fun PlaybackOrchestrator.playThree() = play(
        sentences = listOf(sentence(0, "Un."), sentence(1, "Deux."), sentence(2, "Trois.")),
        voiceProfile = profile,
        startFrom = 0,
        publicationId = "pub",
        chapterIndex = 0,
        resourceHref = "ch0.xhtml",
    )

    /**
     * Bug réel corrigé (audit UX D46) : pendant l'attente de synthèse, Pause
     * arrêtait la session (mini-lecteur retiré, aucune reprise possible), et
     * la pause de la notification ne faisait rien.
     */
    @Test
    fun une_pause_pendant_la_synthese_suspend_la_session_et_la_reprise_relance() = runBlocking<Unit> {
        val orchestrator = orchestrator(HangingTtsEngine())
        try {
            orchestrator.playThree()
            assertEquals(PlaybackOrchestrator.PlaybackStatus.Buffering, orchestrator.state.value)

            orchestrator.togglePlayPause()

            assertEquals(PlaybackOrchestrator.PlaybackStatus.Paused, orchestrator.state.value)
            assertTrue("la session reste engagée", orchestrator.isSessionEngaged())

            orchestrator.togglePlayPause()
            assertEquals("la reprise relance la synthèse", PlaybackOrchestrator.PlaybackStatus.Buffering, orchestrator.state.value)
        } finally {
            orchestrator.stop()
        }
    }

    /**
     * Bug réel corrigé (audit UX D46) : un saut de phrase en pause déplaçait
     * la position sans déplacer le son ; la reprise continuait l'ancienne
     * phrase.
     */
    @Test
    fun un_saut_de_phrase_en_pause_fait_reprendre_a_la_nouvelle_phrase() = runBlocking<Unit> {
        val audioPlayer = FakeAudioPlayer()
        val orchestrator = orchestrator(TimedTtsEngine(durationMs = 300), audioPlayer)
        try {
            orchestrator.playThree()
            withTimeout(5_000) { orchestrator.state.first { it == PlaybackOrchestrator.PlaybackStatus.Playing } }
            orchestrator.pause()

            orchestrator.skip(+1)
            orchestrator.resume()

            assertEquals(1, orchestrator.narrationPosition.value.sentenceIndex)
            val afterPause = audioPlayer.events.dropWhile { it != "pause" }
            assertTrue("le lecteur n'a pas repris l'ancienne file : $afterPause", "resume" !in afterPause)
            withTimeout(5_000) { orchestrator.state.first { it == PlaybackOrchestrator.PlaybackStatus.Playing } }
        } finally {
            orchestrator.stop()
        }
    }

    private fun sentence(index: Int, text: String) =
        Sentence(index = index, text = text, startOffset = index * 10, endOffset = index * 10 + text.length)

    @Test
    fun apres_une_pause_la_phrase_suivante_attend_la_fin_reelle_de_la_phrase_en_cours() = runBlocking<Unit> {
        val orchestrator = PlaybackOrchestrator(
            ttsEngine = TimedTtsEngine(durationMs = 300),
            audioPlayer = FakeAudioPlayer(),
            updateReadingState = UpdateReadingStateUseCase(FakeReadingStateRepository()),
            getReadingState = GetReadingStateUseCase(FakeReadingStateRepository()),
            chapterParser = com.inktone.core.testing.fake.FakeChapterParser(),
            publicationRepository = FakePublicationRepository(),
            publicationParser = FakePublicationParser(),
            preferencesRepository = FakePreferencesRepository(),
            voiceProfileRepository = FakeVoiceProfileRepository(),
            ttsSegmentCache = FakeTtsSegmentCache(),
            pronunciationRuleRepository = FakePronunciationRuleRepository(),
        )
        val profile = VoiceProfile(id = "vp", engine = TtsEngineId.ANDROID_NATIVE, voice = "v", language = "fr-FR")
        try {
            orchestrator.play(
                sentences = listOf(sentence(0, "Un."), sentence(1, "Deux."), sentence(2, "Trois.")),
                voiceProfile = profile,
                startFrom = 0,
                publicationId = "pub",
                chapterIndex = 0,
                resourceHref = "ch0.xhtml",
            )
            withTimeout(5_000) { orchestrator.state.first { it == PlaybackOrchestrator.PlaybackStatus.Playing } }

            orchestrator.pause()
            delay(3_000)
            orchestrator.resume()

            withTimeout(10_000) { orchestrator.narrationPosition.first { it.sentenceIndex == 1 } }
            val secondStartedAt = System.nanoTime()
            withTimeout(5_000) { orchestrator.narrationPosition.first { it.sentenceIndex == 2 } }
            val secondLastedMs = (System.nanoTime() - secondStartedAt) / 1_000_000L

            // Une phrase occupe 950 ms (300 ms de son, 650 ms de silence).
            // Avant le correctif, la pause (3 s) comptait comme du son déjà
            // joué : la phrase 2 était « avancée » aussitôt la phrase 1
            // affichée, devant le son.
            assertTrue("phrase 1 affichée seulement $secondLastedMs ms", secondLastedMs >= 300)
        } finally {
            orchestrator.stop()
        }
    }
}
