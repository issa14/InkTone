package com.inktone.feature.reader

import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioPlayer
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.service.WordTimestamp
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ordonnanceur de lecture gapless (Lot 15, ADR-025) — **borné**, sans preWarm,
 * sans seuils d'erreur adaptatifs, sans sleep timer, sans audio focus. Il ne
 * connaît que les contrats domaine [TtsEngine] et [AudioPlayer] — jamais
 * `AudioTrack`, jamais un moteur concret.
 *
 * Architecture producteur/consommateur :
 * - **Producteur** : synthétise les phrases dans l'ordre, avec un [Channel]
 *   borné à [LOOKAHEAD] (la synthèse de la phrase n+2 chevauche ainsi la
 *   lecture de la phrase n, sans courir plus loin que nécessaire). Un timeout
 *   de synthèse **unique** pour tous les moteurs ; en cas d'échec, un silence
 *   court est injecté et la lecture poursuit.
 * - **Consommateur** : enfile chaque [AudioSegment] + son silence ponctué dans
 *   le lecteur, appelle `play()` au premier, puis **pace** l'avancement de
 *   l'index de phrase à la vitesse de lecture (durées cumulées) — c'est ce qui
 *   déclenche le surlignage côté ViewModel, sans flux de position (LOT 16).
 *
 * [playGeneration] (AtomicLong) invalide les coroutines d'une génération
 * précédente : toute relance (ou arrêt, Tâche 3.2) fait sortir proprement le
 * producteur et le consommateur de l'ancienne génération — jamais deux
 * pipelines en concurrence.
 */
@Singleton
class PlaybackOrchestrator @Inject constructor(
    private val ttsEngine: TtsEngine,
    private val audioPlayer: AudioPlayer,
    private val updateReadingState: UpdateReadingStateUseCase,
) {

    /** État de lecture exposé à la couche présentation. */
    sealed interface PlaybackStatus {
        data object Idle : PlaybackStatus
        data object Buffering : PlaybackStatus
        data object Playing : PlaybackStatus
        data object Paused : PlaybackStatus
        data class Error(val message: String) : PlaybackStatus
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<PlaybackStatus>(PlaybackStatus.Idle)
    val state: StateFlow<PlaybackStatus> = _state.asStateFlow()

    /** Index de la phrase en cours de lecture (surlignage, progression). */
    private val _currentSentenceIndex = MutableStateFlow(0)
    val currentSentenceIndex: StateFlow<Int> = _currentSentenceIndex.asStateFlow()

    /** Timestamps mot-à-mot de la phrase courante — consommés par le surlignage. */
    private val _currentWordTimestamps = MutableStateFlow<List<WordTimestamp>>(emptyList())
    val currentWordTimestamps: StateFlow<List<WordTimestamp>> = _currentWordTimestamps.asStateFlow()

    /**
     * Intervalle de caractères du mot courant, déduit de la position jouée
     * (Lot 16, Tâche 2.1). `null` hors des bornes de tout mot ou quand la
     * position est invalide — le consommateur (ReaderViewModel) retombe alors
     * sur son repli `delay()`.
     */
    private val _currentWordRange = MutableStateFlow<IntRange?>(null)
    val currentWordRange: StateFlow<IntRange?> = _currentWordRange.asStateFlow()

    /**
     * Vrai quand [AudioPlayer.playbackPosition] est valide. Reflète le flux du
     * lecteur : faux avant le premier échantillon, vrai pendant la lecture,
     * faux à l'arrêt. Consommé par le ReaderViewModel pour choisir entre le
     * surlignage par position et le repli `delay()`.
     */
    private val _positionValid = MutableStateFlow(false)
    val positionValid: StateFlow<Boolean> = _positionValid.asStateFlow()

    private val playGeneration = AtomicLong(0)
    private var playbackJob: Job? = null
    private var wordTrackingJob: Job? = null

    init {
        // Miroir de la validité de la position du lecteur, sans verrou ni
        // contention (simple collecte d'un StateFlow) — c'est le signal qui
        // fait basculer le ReaderViewModel vers le repli `delay()`.
        scope.launch {
            audioPlayer.playbackPosition.collect { position ->
                _positionValid.value = position.valid
            }
        }
    }

    /** Sérialise pause/resume/stop (appelés depuis UI, MediaSession, etc.). */
    private val stateLock = ReentrantLock()

    /**
     * Lance la lecture gapless à partir de [startFrom]. Requiert `startFrom`
     * dans les bornes de [sentences] (garanti par l'appelant).
     */
    fun play(
        sentences: List<Sentence>,
        voiceProfile: VoiceProfile,
        startFrom: Int,
        publicationId: String,
        chapterIndex: Int,
        resourceHref: String,
    ) {
        if (sentences.isEmpty()) return
        stop()
        val generation = playGeneration.incrementAndGet()
        _currentSentenceIndex.value = startFrom
        _state.value = PlaybackStatus.Buffering
        playbackJob = scope.launch {
            run(generation, sentences, voiceProfile, startFrom, publicationId, chapterIndex, resourceHref)
        }
    }

    /** Suspend la lecture sans vider la file ni perdre la position. */
    fun pause() {
        stateLock.lock()
        try {
            if (_state.value != PlaybackStatus.Playing) return
            _state.value = PlaybackStatus.Paused
            audioPlayer.pause()
        } finally {
            stateLock.unlock()
        }
    }

    /** Reprend la lecture après [pause]. */
    fun resume() {
        stateLock.lock()
        try {
            if (_state.value != PlaybackStatus.Paused) return
            _state.value = PlaybackStatus.Playing
            audioPlayer.resume()
        } finally {
            stateLock.unlock()
        }
    }

    /** Arrête la lecture, vide la file du lecteur et invalide la génération courante. */
    fun stop() {
        stateLock.lock()
        try {
            playGeneration.incrementAndGet()
            playbackJob?.cancel()
            playbackJob = null
            wordTrackingJob?.cancel()
            wordTrackingJob = null
            audioPlayer.stop()
            _currentWordRange.value = null
            _positionValid.value = false
            _state.value = PlaybackStatus.Idle
        } finally {
            stateLock.unlock()
        }
    }

    private suspend fun run(
        generation: Long,
        sentences: List<Sentence>,
        voiceProfile: VoiceProfile,
        startFrom: Int,
        publicationId: String,
        chapterIndex: Int,
        resourceHref: String,
    ) {
        val channel = Channel<AudioSegment>(LOOKAHEAD)
        val producer = scope.launch { produce(generation, channel, sentences, voiceProfile, startFrom) }
        try {
            consume(generation, channel, sentences, startFrom, publicationId, chapterIndex, resourceHref)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isCurrent(generation)) {
                _state.value = PlaybackStatus.Error(e.message ?: "Erreur de lecture")
            }
        } finally {
            producer.cancel()
            channel.close()
        }
    }

    /** Producteur : synthétise dans l'ordre, timeout unique, silence court sur erreur. */
    private suspend fun produce(
        generation: Long,
        channel: Channel<AudioSegment>,
        sentences: List<Sentence>,
        voiceProfile: VoiceProfile,
        startFrom: Int,
    ) {
        try {
            for (i in startFrom until sentences.size) {
                if (!isCurrent(generation)) break
                val segment = try {
                    withTimeout(SYNTHESIS_TIMEOUT_MS) {
                        ttsEngine.synthesize(sentences[i], voiceProfile)
                    }
                } catch (e: TimeoutCancellationException) {
                    silence(TIMEOUT_SILENCE_MS, audioPlayer.sampleRate)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    silence(TIMEOUT_SILENCE_MS, audioPlayer.sampleRate)
                }
                if (!isCurrent(generation)) break
                channel.send(segment)
            }
        } finally {
            channel.close()
        }
    }

    /**
     * Consommateur : enfile segment + silence, `play()` au premier, et pace
     * l'index de phrase sur une **timeline absolue** (cumul des durées depuis
     * le `play()`), indépendamment du temps passé à attendre la synthèse.
     * Chaque segment suivant est enfilé dès qu'il arrive (le producteur court
     * à LOOKAHEAD) pour rester gapless.
     */
    private suspend fun consume(
        generation: Long,
        channel: Channel<AudioSegment>,
        sentences: List<Sentence>,
        startFrom: Int,
        publicationId: String,
        chapterIndex: Int,
        resourceHref: String,
    ) {
        var index = startFrom
        var playStartNanos = 0L
        var nextPhraseStartMs = 0L
        var first = true

        for (segment in channel) {
            if (!isCurrent(generation)) return
            val sentence = sentences.getOrNull(index)
            val silenceMs = silenceDurationFor(sentence?.text ?: "")
            val phraseDurationMs = segment.durationMs + silenceMs
            // Position jouée au début de l'audio de cette phrase (cumul des
            // durées précédentes) — repère du surlignage mot par position.
            val sentenceStartMs = nextPhraseStartMs

            audioPlayer.sampleRate = segment.sampleRate
            audioPlayer.enqueue(segment)
            audioPlayer.enqueue(silence(silenceMs, segment.sampleRate))

            if (first) {
                audioPlayer.play()
                _state.value = PlaybackStatus.Playing
                playStartNanos = System.nanoTime()
                advanceTo(generation, index, publicationId, chapterIndex, resourceHref, sentence, segment.wordTimestamps)
                first = false
            } else {
                // Pace jusqu'au début ABSOLU de cette phrase (cumul des durées),
                // pas un délai relatif : le temps passé à attendre la synthèse
                // de cette phrase ne doit pas décaler le surlignage.
                val elapsedMs = (System.nanoTime() - playStartNanos) / 1_000_000L
                val waitMs = nextPhraseStartMs - elapsedMs
                if (waitMs > 0) pace(generation, waitMs)
                advanceTo(generation, index, publicationId, chapterIndex, resourceHref, sentence, segment.wordTimestamps)
            }
            launchWordTracking(generation, sentenceStartMs, segment.wordTimestamps)
            nextPhraseStartMs += phraseDurationMs
            index++
        }

        if (isCurrent(generation)) {
            if (!first) {
                val elapsedMs = (System.nanoTime() - playStartNanos) / 1_000_000L
                val waitMs = nextPhraseStartMs - elapsedMs
                if (waitMs > 0) pace(generation, waitMs)
            }
            _state.value = PlaybackStatus.Idle
        }
    }

    /**
     * Avance l'index de phrase courant et persiste la position de reprise
     * (progression à la phrase — jamais au mot, écart déclaré).
     */
    private suspend fun advanceTo(
        generation: Long,
        index: Int,
        publicationId: String,
        chapterIndex: Int,
        resourceHref: String,
        sentence: Sentence?,
        wordTimestamps: List<WordTimestamp>,
    ) {
        if (!isCurrent(generation)) return
        _currentSentenceIndex.value = index
        _currentWordTimestamps.value = wordTimestamps
        if (publicationId.isNotEmpty() && sentence != null) {
            updateReadingState(
                ReadingState(
                    publicationId = publicationId,
                    locator = sentence.startLocator(chapterIndex = chapterIndex, resourceHref = resourceHref),
                    lastReadAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Attend [durationMs] par pas courts, en suspendant pendant une pause et
     * en s'interrompant si la génération est périmée. Approximatif par nature
     * (durées cumulées, pas position AudioTrack) — la synchronisation « par
     * position réelle » est reportée au LOT 16.
     */
    private suspend fun pace(generation: Long, durationMs: Long) {
        var remaining = durationMs
        while (remaining > 0 && isCurrent(generation)) {
            if (_state.value == PlaybackStatus.Paused) {
                delay(PACE_STEP_MS)
                continue
            }
            val step = minOf(remaining, PACE_STEP_MS)
            delay(step)
            remaining -= step
        }
    }

    /**
     * Surlignage mot par position (Lot 16, Tâche 2.1) : un coroutine par
     * phrase déduit [wordRangeAt] de la position jouée et met à jour
     * [_currentWordRange]. Si la position est invalide, aucune plage n'est
     * émise (le consommateur retombe sur le repli `delay()`). Sort dès que la
     * position dépasse le dernier mot, ou que la génération est périmée.
     */
    private fun launchWordTracking(generation: Long, sentenceStartMs: Long, wordTimestamps: List<WordTimestamp>) {
        wordTrackingJob?.cancel()
        _currentWordRange.value = null
        if (wordTimestamps.isEmpty()) return
        val lastEndMs = sentenceStartMs + wordTimestamps.last().endMs
        wordTrackingJob = scope.launch {
            while (isCurrent(generation)) {
                if (_state.value == PlaybackStatus.Paused) {
                    delay(WORD_TRACKING_STEP_MS)
                    continue
                }
                val position = audioPlayer.playbackPosition.value
                val range = if (position.valid) {
                    wordRangeAt(position.playedMs, sentenceStartMs, wordTimestamps)
                } else {
                    null
                }
                if (_currentWordRange.value != range) {
                    _currentWordRange.value = range
                }
                if (position.valid && position.playedMs >= lastEndMs) return@launch
                delay(WORD_TRACKING_STEP_MS)
            }
        }
    }

    private fun isCurrent(generation: Long): Boolean = playGeneration.get() == generation

    /** Génère un silence PCM16 de [durationMs] au [sampleRate] donné. */
    private fun silence(durationMs: Long, sampleRate: Int): AudioSegment {
        val frameCount = (sampleRate * durationMs / 1000L).toInt().coerceAtLeast(1)
        return AudioSegment(
            audioData = ByteArray(frameCount * 2),
            durationMs = durationMs,
            wordTimestamps = emptyList(),
            sampleRate = sampleRate,
        )
    }

    private fun silenceDurationFor(text: String): Long {
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return SILENCE_SENTENCE_MS
        return when (trimmed.last()) {
            ',', ';' -> SILENCE_COMMA_MS
            '.', '!', '?', '\u2026' -> SILENCE_SENTENCE_MS
            '\n' -> SILENCE_PARAGRAPH_MS
            else -> SILENCE_SENTENCE_MS
        }
    }

    private companion object {
        const val LOOKAHEAD = 3
        /** Timeout de synthèse unique (Edge cloud et Sherpa-ONNX confondus). */
        const val SYNTHESIS_TIMEOUT_MS = 20_000L
        /** Silence injecté après une synthèse en échec. */
        const val TIMEOUT_SILENCE_MS = 50L
        const val PACE_STEP_MS = 50L
        const val WORD_TRACKING_STEP_MS = 20L
        const val SILENCE_COMMA_MS = 150L
        const val SILENCE_SENTENCE_MS = 650L
        const val SILENCE_PARAGRAPH_MS = 1000L
    }
}
