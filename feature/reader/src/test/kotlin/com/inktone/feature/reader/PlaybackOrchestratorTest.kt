package com.inktone.feature.reader

import com.inktone.core.testing.fake.FakeChapterParser
import com.inktone.core.testing.fake.FakeVoiceProfileRepository
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlaybackEvent
import com.inktone.domain.service.TtsCapabilities
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests JVM de [PlaybackOrchestrator] (Tâche 3.4) : enchaînement segments +
 * silences dans le bon ordre, pause/reprise (index conservé), stop (producteur
 * annulé), erreur de synthèse (silence court + poursuite), fin de chapitre
 * (Idle + progression sauvegardée). Fakes [TtsEngine] et [AudioPlayer] — aucune
 * dépendance Android.
 */
class PlaybackOrchestratorTest {

    private val profile = VoiceProfile(
        id = "vp-test",
        engine = TtsEngineId.ANDROID_NATIVE,
        voice = "fr-fr",
        language = "fr-FR",
    )

    private fun sentence(index: Int, text: String, startOffset: Int) =
        Sentence(index = index, text = text, startOffset = startOffset, endOffset = startOffset + text.length)

    private suspend fun awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !condition()) {
            delay(10)
        }
        assertTrue("condition non atteinte en $timeoutMs ms", condition())
    }

    private fun assertSilence(segment: AudioSegment, expectedDurationMs: Long) {
        assertEquals(expectedDurationMs, segment.durationMs)
        assertTrue("audioData doit être silencieux", segment.audioData.all { it == 0.toByte() })
    }

    @Test
    fun enchaineSegmentsEtSilencesDansLordre() = runBlocking {
        val tts = FakeTtsEngine(segmentDurationMs = 200)
        val player = FakeAudioPlayer()
        val orchestrator = PlaybackOrchestrator(tts, player, UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository())
        val sentences = listOf(
            sentence(0, "Bonjour.", 0),
            sentence(1, "Salut,", 8),
            sentence(2, "Fin!", 14),
        )

        orchestrator.play(sentences, profile, 0, "pub1", 1, "ch1.xhtml")
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Idle }

        // seg0, sil(650), seg1, sil(150), seg2, sil(650)
        assertEquals(6, player.enqueued.size)
        assertEquals(1, player.enqueued[0].audioData[0].toInt())
        assertSilence(player.enqueued[1], 650)
        assertEquals(2, player.enqueued[2].audioData[0].toInt())
        assertSilence(player.enqueued[3], 150)
        assertEquals(3, player.enqueued[4].audioData[0].toInt())
        assertSilence(player.enqueued[5], 650)
        assertEquals("play() doit être appelé une seule fois", 1, player.events.count { it == "play" })
        assertEquals(PlaybackOrchestrator.PlaybackStatus.Idle, orchestrator.state.value)
    }

    @Test
    fun pauseRepriseConserveIndex() = runBlocking {
        val tts = FakeTtsEngine(segmentDurationMs = 500)
        val player = FakeAudioPlayer()
        val orchestrator = PlaybackOrchestrator(tts, player, UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository())
        val sentences = listOf(sentence(0, "Un.", 0), sentence(1, "Deux.", 4), sentence(2, "Trois.", 10))

        orchestrator.play(sentences, profile, 0, "pub1", 0, "ch.xhtml")
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Playing }
        assertEquals(0, orchestrator.currentSentenceIndex.value)

        orchestrator.pause()
        assertEquals(PlaybackOrchestrator.PlaybackStatus.Paused, orchestrator.state.value)
        Thread.sleep(300)
        assertEquals("l'index ne doit pas avancer pendant la pause", 0, orchestrator.currentSentenceIndex.value)

        orchestrator.resume()
        assertEquals(PlaybackOrchestrator.PlaybackStatus.Playing, orchestrator.state.value)

        orchestrator.stop()
        assertEquals(PlaybackOrchestrator.PlaybackStatus.Idle, orchestrator.state.value)
    }

    @Test
    fun stopAnnuleLeProducteur() = runBlocking {
        val tts = FakeTtsEngine(segmentDurationMs = 200, synthesisDelayMs = 30)
        val orchestrator = PlaybackOrchestrator(tts, FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository())
        val sentences = (0 until 200).map { sentence(it, "Phrase $it.", it * 8) }

        orchestrator.play(sentences, profile, 0, "pub1", 0, "ch.xhtml")
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Playing }
        Thread.sleep(150) // laisse le producteur synthétiser quelques phrases
        orchestrator.stop()
        assertEquals(PlaybackOrchestrator.PlaybackStatus.Idle, orchestrator.state.value)

        val count1 = tts.synthesizeCount.get()
        Thread.sleep(300)
        val count2 = tts.synthesizeCount.get()
        assertTrue("le producteur doit cesser de synthétiser après stop", count2 <= count1 + 1)
        assertTrue("le producteur ne doit pas avoir tout synthétisé", count2 < sentences.size)
    }

    @Test
    fun erreurSynthese_injecteUnSilenceCourt_puisPoursuit() = runBlocking {
        val tts = FakeTtsEngine(segmentDurationMs = 200, failSynthesisCount = 1)
        val player = FakeAudioPlayer()
        val orchestrator = PlaybackOrchestrator(tts, player, UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository())
        val sentences = listOf(sentence(0, "Première.", 0), sentence(1, "Deuxième.", 10))

        orchestrator.play(sentences, profile, 0, "pub1", 0, "ch.xhtml")
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Idle }

        // silence court (50 ms) + silence ponctué (650) pour la phrase 0 en
        // échec, puis seg1 + silence ponctué (650).
        assertEquals(4, player.enqueued.size)
        assertSilence(player.enqueued[0], 50)
        assertSilence(player.enqueued[1], 650)
        assertEquals(2, player.enqueued[2].audioData[0].toInt())
        assertSilence(player.enqueued[3], 650)
    }

    @Test
    fun finDeChapitre_passeIdle_etSauvegardeProgression() = runBlocking {
        val tts = FakeTtsEngine(segmentDurationMs = 100)
        val player = FakeAudioPlayer()
        val repo = FakeReadingStateRepository()
        val orchestrator = PlaybackOrchestrator(tts, player, UpdateReadingStateUseCase(repo), GetReadingStateUseCase(repo), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository())
        val sentences = listOf(sentence(0, "Un.", 0), sentence(1, "Deux.", 4))

        orchestrator.play(sentences, profile, 0, "pub1", 3, "ch3.xhtml")
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Idle }

        assertEquals(2, repo.saved.size)
        assertEquals("pub1", repo.saved[0].publicationId)
        assertEquals(3, repo.saved[0].locator.chapterIndex)
        assertEquals("ch3.xhtml", repo.saved[0].locator.resourceHref)
        assertEquals(0, repo.saved[0].locator.charOffset)
        assertEquals(4, repo.saved[1].locator.charOffset)
    }

    @Test
    fun lAvancementDePhrase_naPasDeRetardDeSynthese() = runBlocking {
        // Synthèse plus lente que la lecture (300 ms) mais qui reste en
        // avance (durée de phrase 350+150=500 ms) : sans la timeline absolue,
        // l'index de phrase se décalait du temps de synthèse (~800 ms au lieu
        // de ~500 ms) — le surlignage mot-à-mot prenait du retard sur Edge.
        val tts = FakeTtsEngine(segmentDurationMs = 350, synthesisDelayMs = 300)
        val player = FakeAudioPlayer()
        val orchestrator = PlaybackOrchestrator(tts, player, UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository())
        val sentences = listOf(sentence(0, "Un,", 0), sentence(1, "Deux,", 4))

        orchestrator.play(sentences, profile, 0, "pub1", 0, "ch.xhtml")
        awaitUntil { orchestrator.currentSentenceIndex.value == 0 && orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Playing }
        val t0 = System.nanoTime()
        awaitUntil { orchestrator.currentSentenceIndex.value == 1 }
        val gapMs = (System.nanoTime() - t0) / 1_000_000L

        assertTrue(
            "passage phrase 0 → 1 en ${gapMs}ms, attendu ~500ms (pas ~800ms décalé de la synthèse)",
            gapMs < 650,
        )
        orchestrator.stop()
    }

    // ── Session (P1, contrat PlaybackSession) ──────────────

    @Test
    fun skip_pendant_lecture_reprend_sur_la_nouvelle_phrase() = runBlocking {
        val tts = FakeTtsEngine(segmentDurationMs = 300)
        val player = FakeAudioPlayer()
        val orchestrator = PlaybackOrchestrator(tts, player, UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository())
        val sentences = listOf(sentence(0, "Un.", 0), sentence(1, "Deux.", 4), sentence(2, "Trois.", 10))

        orchestrator.play(sentences, profile, 0, "pub1", 0, "ch.xhtml")
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Playing }

        orchestrator.skip(1)

        awaitUntil { orchestrator.isPlaying.value }
        assertEquals(1, orchestrator.currentSentenceIndex.value)
        orchestrator.stop()
    }

    @Test
    fun skip_a_larret_deplace_lindex_sans_reprendre() = runBlocking {
        val orchestrator = PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository())
        val sentences = listOf(sentence(0, "Un.", 0), sentence(1, "Deux.", 4), sentence(2, "Trois.", 10))
        orchestrator.play(sentences, profile, 0, "pub1", 0, "ch.xhtml")
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Playing }
        orchestrator.stop()
        assertEquals(PlaybackOrchestrator.PlaybackStatus.Idle, orchestrator.state.value)

        orchestrator.skip(1)

        assertEquals(1, orchestrator.currentSentenceIndex.value)
        assertEquals(PlaybackOrchestrator.PlaybackStatus.Idle, orchestrator.state.value)
        // isPlaying est un flux dérivé (stateIn sur Dispatchers.IO) : sa
        // propagation est asynchrone, on attend plutôt que d'asserter aussitôt.
        awaitUntil { !orchestrator.isPlaying.value }
    }

    @Test
    fun togglePlayPause_bascule_entre_pause_et_reprise() = runBlocking {
        val orchestrator = PlaybackOrchestrator(FakeTtsEngine(segmentDurationMs = 300), FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository())
        val sentences = listOf(sentence(0, "Un.", 0), sentence(1, "Deux.", 4))
        orchestrator.play(sentences, profile, 0, "pub1", 0, "ch.xhtml")
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Playing }

        // `isPlaying` est un `stateIn` : il suit `state` par une coroutine, il
        // ne bascule donc pas dans le même tick. L'attendre au lieu de
        // l'affirmer aussitôt — sinon le test passe ou échoue selon
        // l'ordonnancement (observé rouge sur une exécution complète).
        orchestrator.togglePlayPause()
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Paused }
        awaitUntil { !orchestrator.isPlaying.value }

        orchestrator.togglePlayPause()
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Playing }
        awaitUntil { orchestrator.isPlaying.value }

        orchestrator.stop()
    }

    @Test
    fun togglePlayPause_apres_arret_relance_depuis_la_session_retenue() = runBlocking {
        val orchestrator = PlaybackOrchestrator(FakeTtsEngine(segmentDurationMs = 300), FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository())
        val sentences = listOf(sentence(0, "Un.", 0), sentence(1, "Deux.", 4))
        orchestrator.play(sentences, profile, 0, "pub1", 0, "ch.xhtml")
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Playing }
        orchestrator.stop()
        assertEquals(PlaybackOrchestrator.PlaybackStatus.Idle, orchestrator.state.value)

        orchestrator.togglePlayPause()

        awaitUntil { orchestrator.isPlaying.value }
        orchestrator.stop()
    }

    @Test
    fun metadata_et_isPlaying_refletent_letat() = runBlocking {
        val orchestrator = PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository())
        orchestrator.setMetadata("pub-1", "Titre de test", "Auteur de test")
        assertEquals("Titre de test", orchestrator.metadata.value.title)
        assertEquals("Auteur de test", orchestrator.metadata.value.author)
        // P2 — adresse de retour du mini-lecteur : sans elle, la barre
        // ramènerait au dernier livre ouvert, pas à celui qui est narré.
        assertEquals("pub-1", orchestrator.metadata.value.publicationId)
        assertEquals(false, orchestrator.isPlaying.value)

        orchestrator.play(listOf(sentence(0, "Un.", 0)), profile, 0, "pub1", 0, "ch.xhtml")
        awaitUntil { orchestrator.isPlaying.value }
        orchestrator.stop()
        awaitUntil { !orchestrator.isPlaying.value }
    }

    // ── Fakes ──────────────────────────────────────────────

    /**
     * P1 — la fin de chapitre a un signal propre. Auparavant le Lecteur la
     * déduisait de « `Idle` alors que la lecture était engagée », ce qui
     * confondait une vraie fin avec une pause demandée pendant la synthèse
     * (`Buffering` → `stop()`) : depuis la notification, mettre en pause au
     * mauvais moment faisait sauter un chapitre.
     */
    @Test
    fun chapterCompletedNestEmisQuALaFinNaturelleDuChapitre() = runBlocking {
        val tts = FakeTtsEngine(segmentDurationMs = 50)
        val player = FakeAudioPlayer()
        val orchestrator = PlaybackOrchestrator(tts, player, UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository())
        val completions = AtomicInteger(0)
        val subscribed = AtomicBoolean(false)
        val collector = CoroutineScope(Dispatchers.Default).launch {
            orchestrator.chapterCompleted
                .onSubscription { subscribed.set(true) }
                .collect { completions.incrementAndGet() }
        }
        // `chapterCompleted` n'a pas de replay : une émission avant l'abonnement
        // serait perdue et rendrait le test faussement vert (ou faussement rouge).
        awaitUntil { subscribed.get() }

        orchestrator.play(listOf(sentence(0, "Fin.", 0)), profile, 0, "pub1", 0, "ch.xhtml")
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Idle }
        awaitUntil { completions.get() == 1 }

        collector.cancel()
    }

    @Test
    fun unePauseDemandeePendantLaSyntheseNemetPasDeFinDeChapitre() = runBlocking {
        // Synthèse lente : l'état reste `Buffering`, où `togglePlayPause`
        // annule la synthèse (retour `Idle`) faute d'audio à suspendre.
        val tts = FakeTtsEngine(segmentDurationMs = 50, synthesisDelayMs = 2_000)
        val player = FakeAudioPlayer()
        val orchestrator = PlaybackOrchestrator(tts, player, UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository())
        val completions = AtomicInteger(0)
        val subscribed = AtomicBoolean(false)
        val collector = CoroutineScope(Dispatchers.Default).launch {
            orchestrator.chapterCompleted
                .onSubscription { subscribed.set(true) }
                .collect { completions.incrementAndGet() }
        }
        // `chapterCompleted` n'a pas de replay : une émission avant l'abonnement
        // serait perdue et rendrait le test faussement vert (ou faussement rouge).
        awaitUntil { subscribed.get() }

        orchestrator.play(listOf(sentence(0, "Une.", 0), sentence(1, "Deux.", 5)), profile, 0, "pub1", 0, "ch.xhtml")
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Buffering }
        orchestrator.togglePlayPause()
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Idle }

        Thread.sleep(200)
        assertEquals("aucune fin de chapitre : l'utilisateur a mis en pause", 0, completions.get())
        collector.cancel()
    }

    /**
     * P1-d — `ReaderViewModel.onCleared()` ne coupe la narration que si aucune
     * session n'est engagée : quitter le Lecteur pendant une lecture (ou une
     * pause réelle, notification affichée) ne doit plus faire taire la voix.
     * Ce test verrouille le prédicat dont dépend cette décision.
     */
    @Test
    fun isSessionEngagedDistingueLectureEtPauseDunArret() = runBlocking {
        val tts = FakeTtsEngine(segmentDurationMs = 400)
        val player = FakeAudioPlayer()
        val orchestrator = PlaybackOrchestrator(tts, player, UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository())

        assertFalse("aucune lecture lancée : rien à préserver", orchestrator.isSessionEngaged())

        orchestrator.play(listOf(sentence(0, "Bonjour.", 0)), profile, 0, "pub1", 0, "ch.xhtml")
        awaitUntil { orchestrator.state.value == PlaybackOrchestrator.PlaybackStatus.Playing }
        assertTrue("lecture en cours", orchestrator.isSessionEngaged())

        orchestrator.pause()
        assertTrue("pause réelle : la notification reste, la session est engagée", orchestrator.isSessionEngaged())

        orchestrator.stop()
        assertFalse("arrêt : plus rien à préserver", orchestrator.isSessionEngaged())
    }

    private class FakeTtsEngine(
        private val segmentDurationMs: Long = 200,
        private val failSynthesisCount: Int? = null,
        private val synthesisDelayMs: Long = 0,
    ) : TtsEngine {
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
        val synthesizeCount = AtomicInteger(0)

        override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment {
            val n = synthesizeCount.incrementAndGet()
            if (synthesisDelayMs > 0) delay(synthesisDelayMs)
            if (failSynthesisCount == n) throw RuntimeException("échec synthèse #$n")
            val marker = (sentence.index + 1).toByte()
            return AudioSegment(
                audioData = ByteArray(4) { marker },
                durationMs = segmentDurationMs,
                wordTimestamps = emptyList(),
                sampleRate = 22_050,
            )
        }

        override fun observePlaybackEvents(): Flow<PlaybackEvent> = emptyFlow()
    }

    private class FakeReadingStateRepository : ReadingStateRepository {
        val saved = CopyOnWriteArrayList<ReadingState>()
        override suspend fun get(publicationId: String): ReadingState? = null
        override fun observe(publicationId: String): Flow<ReadingState?> = emptyFlow()
        override suspend fun getAll(): List<ReadingState> = emptyList()
        override suspend fun save(state: ReadingState) {
            saved.add(state)
        }

        override suspend fun delete(publicationId: String) = Unit
    }
}
