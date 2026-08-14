package com.inktone.infrastructure.media

import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Machine d'états + file + synchronisation du lecteur gapless, extraites de la
 * couche I/O `AudioTrack` pour être testables en JVM (Tâche 1.3). Cette classe
 * est pure Kotlin : aucune dépendance Android. Le lecteur réel
 * ([GaplessAudioPlayer]) lui fournit un [PcmSink] qui encapsule la seule chose
 * qui ne se teste pas sur JVM — l'`AudioTrack` — et possède le [scope] de
 * coroutines.
 *
 * Responsabilités (et rien de plus) :
 * - **File non-bloquante** : [ConcurrentLinkedQueue] + [Semaphore]. [enqueue]
 *   ne bloque jamais le producteur, même si le consommateur est arrêté
 *   (remplace un `Channel.send` qui suspendait indéfiniment quand le
 *   consommateur s'arrêtait — deadlock observé sur le legacy).
 * - **Machine d'états** : [PlayerState] émis en [StateFlow], transitions
 *   `play`/`pause`/`resume`/`stop`.
 * - **Synchronisation anti-SIGSEGV** : [ReentrantLock] + flag atomique
 *   [willStop] autour de chaque écriture et de la libération du track. La
 *   libération ne peut jamais concurrencer une écriture en cours.
 *
 * Cette classe ne connaît ni phrase, ni chapitre, ni surlignage : c'est un
 * consommateur passif. L'ordre des segments et des silences est la
 * responsabilité de l'ordonnanceur (couche présentation).
 *
 * **Aucun flux de position** : la synchronisation du surlignage par position
 * réelle est reportée au LOT 16.
 *
 * @param sink couche I/O fine (l'`AudioTrack`) — jamais `AudioTrack` ici.
 * @param scope scope possédé par le lecteur ; les coroutines de consommation
 *   y sont lancées.
 * @param pollTimeoutMs temps d'attente max du consommateur entre deux
 *   vérifications d'état quand la file est vide (legacy : 200 ms). Exposé en
 *   paramètre pour raccourcir les tests JVM, inchangé en production.
 */
class GaplessPlaybackCore(
    private val sink: PcmSink,
    private val scope: CoroutineScope,
    private val pollTimeoutMs: Long = DEFAULT_POLL_TIMEOUT_MS,
) {

    /**
     * Couche I/O réelle du lecteur, injectée par [GaplessAudioPlayer]. Toutes
     * les méthodes sont appelées sous [ReentrantLock] d'écriture (sauf
     * [PcmSink.setTrackVolume], qui peut être appelée hors verrou) : une
     * implémentation n'a pas à se soucier des courses de libération.
     */
    interface PcmSink {
        /** Crée (ou recrée si le sampleRate a changé) le track pour [sampleRate]. */
        fun ensureTrack(sampleRate: Int)

        /** Écrit [length] octets de [data] à partir de [offset]. Retourne le nombre d'octets écrits, ou une valeur ≤ 0 en erreur. */
        fun write(data: ByteArray, offset: Int, length: Int): Int

        /** Suspend la lecture du track sans le libérer. */
        fun pauseTrack()

        /** Reprend la lecture du track. */
        fun resumeTrack()

        /** Arrête et libère le track. Idempotent (sûr si track déjà nul). */
        fun stopAndReleaseTrack()

        /** Règle le volume du track (`0.0` = silence, `1.0` = max). */
        fun setTrackVolume(volume: Float)
    }

    private val queue = ConcurrentLinkedQueue<AudioSegment>()
    private val queueSemaphore = Semaphore(0)

    /**
     * Verrou protégeant le cycle de vie du track contre le use-after-free.
     * [stop] l'acquiert avant de libérer le track, et le consommateur
     * l'acquiert avant chaque écriture : aucune écriture ne peut être en
     * cours pendant la libération (crash natif SIGSEGV du legacy éliminé).
     */
    private val writeLock = ReentrantLock()

    /**
     * Flag atomique demandant l'arrêt. Vérifié sous [writeLock] avant chaque
     * écriture : sortie propre du consommateur avant même l'annulation de son
     * job, évitant tout use-after-free sur le track.
     */
    @Volatile
    private var willStop = false

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Nombre de segments encore en attente (non écrits). */
    val pendingCount: Int get() = queue.size

    /** Fréquence d'échantillonnage (Hz) de configuration du track. */
    @Volatile
    var sampleRate: Int = DEFAULT_SAMPLE_RATE

    private var consumerJob: Job? = null

    /** Ajoute un segment en file. Non-bloquant, retour immédiat. */
    fun enqueue(segment: AudioSegment) {
        queue.add(segment)
        queueSemaphore.release()
    }

    /** Démarre (ou reprend) la lecture. Idempotent si déjà en lecture. */
    fun play() {
        if (_state.value == PlayerState.Playing) return
        willStop = false
        _state.value = PlayerState.Playing
        ensureConsumerRunning()
    }

    /** Suspend la lecture sans vider la file ni perdre la position. */
    fun pause() {
        if (_state.value != PlayerState.Playing) return
        _state.value = PlayerState.Paused
        sink.pauseTrack()
    }

    /** Reprend la lecture là où [pause] l'avait laissée. */
    fun resume() {
        if (_state.value != PlayerState.Paused) return
        _state.value = PlayerState.Playing
        sink.resumeTrack()
        ensureConsumerRunning()
    }

    /** Arrête la lecture et vide la file des segments en attente. */
    fun stop() {
        willStop = true
        _state.value = PlayerState.Stopped
        consumerJob?.cancel()

        queue.clear()
        queueSemaphore.drainPermits()

        writeLock.lock()
        try {
            sink.stopAndReleaseTrack()
        } finally {
            writeLock.unlock()
        }
    }

    /** Libère définitivement : arrête, puis revient à l'état [PlayerState.Idle]. */
    fun release() {
        stop()
        _state.value = PlayerState.Idle
    }

    /** Règle le volume (`0.0` = silence, `1.0` = max). */
    fun setVolume(volume: Float) {
        sink.setTrackVolume(volume.coerceIn(0f, 1f))
    }

    /**
     * Lance le consommateur une seule fois par cycle de lecture ; il survit à
     * [pause]/[resume] (il s'endort via [delay] tant que l'état n'est pas
     * [PlayerState.Playing]). Ne jamais relancer à la reprise : relancer
     * ouvrirait une course de double consommation si l'ancien consommateur
     * était encore en train d'écrire.
     */
    private fun ensureConsumerRunning() {
        if (consumerJob?.isActive == true) return
        consumerJob = scope.launch {
            // ensureTrack est différé au premier segment écrit (écart 1 : le
            // track est créé à la volée au sampleRate configuré, pas en avance).
            while (isActive) {
                if (_state.value == PlayerState.Playing) {
                    if (queueSemaphore.tryAcquire(pollTimeoutMs, TimeUnit.MILLISECONDS)) {
                        queue.poll()?.let { writeSegment(it) }
                    }
                } else {
                    // Paused (ou Idle initial) : suspend au lieu de tourner à
                    // vide — pas de busy spin pendant une pause.
                    delay(pollTimeoutMs)
                }
                yield()
            }
        }
    }

    /**
     * Écrit un segment PCM16 par chunks, en acquérant le verrou d'écriture à
     * CHAQUE chunk (et non pour tout le segment) : [stop] peut ainsi saisir le
     * verrou entre deux chunks et libérer le track sans attendre la fin d'un
     * segment entier. Le PCM16 est écrit directement, sans conversion ni gain
     * (écart 1 vis-à-vis du legacy FloatArray).
     */
    private fun writeSegment(segment: AudioSegment) {
        val data = segment.audioData
        var offset = 0
        while (offset < data.size) {
            writeLock.lock()
            try {
                if (willStop || _state.value != PlayerState.Playing) return
                if (offset == 0) sink.ensureTrack(sampleRate)
                val length = minOf(CHUNK_SIZE_BYTES, data.size - offset)
                val written = sink.write(data, offset, length)
                if (written <= 0) return
                offset += written
            } finally {
                writeLock.unlock()
            }
        }
    }

    private companion object {
        const val DEFAULT_POLL_TIMEOUT_MS = 200L
        const val DEFAULT_SAMPLE_RATE = 22_050
        const val CHUNK_SIZE_BYTES = 8192
    }
}
