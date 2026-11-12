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
 * Audit UX D40 — un moteur en panne ne doit pas faire « lire » le livre en
 * silence : la narration s'arrête en erreur après quelques échecs consécutifs.
 */
class PlaybackOrchestratorSynthesisFailureTest {

    private class FailingTtsEngine : com.inktone.domain.service.TtsEngine {
        override val id = TtsEngineId.ANDROID_NATIVE
        override val capabilities = com.inktone.domain.service.TtsCapabilities(
            offline = true, wordTimestamps = false, sentenceTimestamps = false, languages = listOf("fr-FR"),
            streamingSynthesis = false, speedControl = false, pitchControl = false, modelSizeMb = 0, license = "test",
        )

        override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): com.inktone.domain.service.AudioSegment =
            throw IllegalStateException("moteur en panne")

        override fun observePlaybackEvents(): kotlinx.coroutines.flow.Flow<com.inktone.domain.service.PlaybackEvent> =
            kotlinx.coroutines.flow.emptyFlow()
    }

    @Test
    fun un_moteur_en_panne_arrete_la_narration_en_erreur_au_lieu_de_lire_en_silence() = runBlocking {
        val orchestrator = PlaybackOrchestrator(
            ttsEngine = FailingTtsEngine(),
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
        val sentences = (0 until 10).map { Sentence(index = it, text = "Phrase $it.", startOffset = it * 20, endOffset = it * 20 + 9) }
        try {
            orchestrator.play(
                sentences = sentences,
                voiceProfile = VoiceProfile(id = "vp", engine = TtsEngineId.ANDROID_NATIVE, voice = "v", language = "fr-FR"),
                startFrom = 0,
                publicationId = "pub",
                chapterIndex = 0,
                resourceHref = "ch0.xhtml",
            )

            val status = withTimeout(10_000) {
                orchestrator.state.first { it is PlaybackOrchestrator.PlaybackStatus.Error || it == PlaybackOrchestrator.PlaybackStatus.Idle }
            }

            assertTrue("arrêt en erreur, pas une fin de chapitre muette : $status", status is PlaybackOrchestrator.PlaybackStatus.Error)
            assertTrue(
                "la position n'a pas traversé le chapitre : ${orchestrator.narrationPosition.value}",
                orchestrator.narrationPosition.value.sentenceIndex < 5,
            )
        } finally {
            orchestrator.stop()
        }
    }
}
