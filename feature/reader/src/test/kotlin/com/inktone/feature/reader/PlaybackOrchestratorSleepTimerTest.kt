package com.inktone.feature.reader

import com.inktone.core.testing.fake.FakeChapterParser
import com.inktone.core.testing.fake.FakeTtsSegmentCache
import com.inktone.core.testing.fake.FakePronunciationRuleRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeVoiceProfileRepository
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlaybackEvent
import com.inktone.domain.service.TtsCapabilities
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Garde-fou de l'écart 4 de P1 : le minuteur de sommeil appartient à la
 * session, pas à l'écran Lecteur.
 *
 * L'intérêt du minuteur est justement de s'appliquer quand personne ne regarde
 * l'écran — et depuis P1-d, quitter le Lecteur ne coupe plus la narration. Un
 * minuteur resté attaché à l'écran laissait donc la voix tourner toute la nuit
 * dès qu'on fermait le Lecteur.
 */
class PlaybackOrchestratorSleepTimerTest {

    private class InstantTtsEngine : TtsEngine {
        override val id = TtsEngineId.ANDROID_NATIVE
        override val capabilities = TtsCapabilities(
            offline = true,
            wordTimestamps = false,
            sentenceTimestamps = false,
            languages = listOf("fr-FR"),
            streamingSynthesis = false,
            speedControl = false,
            pitchControl = false,
            modelSizeMb = 0,
            license = "test",
        )

        override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile) = AudioSegment(
            audioData = ByteArray(4),
            // Long, pour que la lecture soit encore en cours quand le minuteur
            // expire : c'est le cas qui compte.
            durationMs = 60_000,
            wordTimestamps = emptyList(),
            sampleRate = 22_050,
        )

        override fun observePlaybackEvents(): Flow<PlaybackEvent> = emptyFlow()
    }

    private val profile = VoiceProfile(
        id = "vp-test",
        engine = TtsEngineId.ANDROID_NATIVE,
        voice = "fr-fr",
        language = "fr-FR",
    )

    private fun orchestrator() = PlaybackOrchestrator(
        ttsEngine = InstantTtsEngine(),
        audioPlayer = FakeAudioPlayer(),
        updateReadingState = UpdateReadingStateUseCase(FakeReadingStateRepository()),
            getReadingState = GetReadingStateUseCase(FakeReadingStateRepository()),
        chapterParser = FakeChapterParser(),
            publicationRepository = FakePublicationRepository(),
            publicationParser = FakePublicationParser(),
            preferencesRepository = FakePreferencesRepository(),
            voiceProfileRepository = FakeVoiceProfileRepository(),
            ttsSegmentCache = FakeTtsSegmentCache(),
            pronunciationRuleRepository = FakePronunciationRuleRepository(),
    )

    @Test
    fun armerLeMinuteurExposeLeTempsRestant() = runBlocking {
        val orchestrator = orchestrator()

        orchestrator.setSleepTimer(15)

        val timer = orchestrator.sleepTimer.value
        assertNotNull("le minuteur doit être visible immédiatement", timer)
        assertEquals(15 * 60_000L, timer!!.remainingMs)
    }

    @Test
    fun reArmerRemplaceAuLieuDeCumuler() = runBlocking {
        val orchestrator = orchestrator()

        orchestrator.setSleepTimer(30)
        orchestrator.setSleepTimer(5)

        // Un seul minuteur actif : le second remplace le premier, il ne
        // s'ajoute pas. Deux minuteurs concurrents couperaient la narration à
        // la première échéance, en contradiction avec le dernier choix fait.
        assertEquals(5 * 60_000L, orchestrator.sleepTimer.value?.remainingMs)
    }

    @Test
    fun annulerRetireLeMinuteur() = runBlocking {
        val orchestrator = orchestrator()

        orchestrator.setSleepTimer(20)
        orchestrator.setSleepTimer(null)

        assertNull(orchestrator.sleepTimer.value)
    }

    @Test
    fun uneDureeNulleOuNegativeNArmeRien() = runBlocking {
        val orchestrator = orchestrator()

        orchestrator.setSleepTimer(0)
        assertNull("0 minute n'est pas un minuteur qui expire aussitôt", orchestrator.sleepTimer.value)

        orchestrator.setSleepTimer(-5)
        assertNull(orchestrator.sleepTimer.value)
    }

    @Test
    fun leMinuteurDecompteEtArreteLaNarrationAExpiration() = runBlocking {
        val orchestrator = orchestrator()
        orchestrator.play(
            sentences = listOf(Sentence(index = 0, text = "Une phrase longue.", startOffset = 0, endOffset = 18)),
            voiceProfile = profile,
            startFrom = 0,
            publicationId = "pub1",
            chapterIndex = 0,
            resourceHref = "ch0.xhtml",
        )
        withTimeout(5_000) { orchestrator.isPlaying.first { it } }

        // 1 minute : le décompte à la seconde émet 60 valeurs avant d'expirer.
        orchestrator.setSleepTimer(1)

        // Attend l'extinction réelle plutôt qu'une durée arbitraire.
        withTimeout(90_000) { orchestrator.sleepTimer.first { it == null } }
        withTimeout(5_000) { orchestrator.isPlaying.first { !it } }

        assertEquals(
            "à l'expiration, la narration doit être ARRÊTÉE, pas seulement le minuteur éteint",
            PlaybackOrchestrator.PlaybackStatus.Idle,
            orchestrator.state.value,
        )
    }
}
