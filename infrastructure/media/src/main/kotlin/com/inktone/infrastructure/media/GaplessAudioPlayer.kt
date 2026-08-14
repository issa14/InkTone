package com.inktone.infrastructure.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.inktone.domain.service.AudioPlayer
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lecteur audio gapless (Lot 15, ADR-025) : implémentation [AudioPlayer] pour
 * l'infrastructure. Un seul [AudioTrack] `MODE_STREAM`, alimenté en continu
 * par une file non-bloquante, pour enchaîner les segments PCM16 sans silence
 * inter-segment.
 *
 * Cette classe est la **couche I/O fine** : elle ne possède ni la machine
 * d'états, ni la file, ni la synchronisation — tout cela vit dans
 * [GaplessPlaybackCore] (pur JVM, testé en Tâche 1.3). Elle fournit au cœur
 * un [GaplessPlaybackCore.PcmSink] qui enveloppe l'[AudioTrack].
 *
 * **PCM16 signé little-endian écrit directement** : aucune conversion
 * Float→Short, aucun gain (écart 1 vis-à-vis du legacy qui compensait le
 * volume faible de Piper par un gain 3×). Le contrat [AudioSegment.audioData]
 * est déjà du PCM16 prêt à écrire.
 *
 * Anti-SIGSEGV : les écritures et la libération du track passent toutes par le
 * verrou du cœur — jamais de double `release()`, jamais d'écriture pendant la
 * libération. La preuve du non-crash reste **instrumentée** (Tâche 2.1), pas
 * JVM.
 */
@Singleton
class GaplessAudioPlayer @Inject constructor() : AudioPlayer, GaplessPlaybackCore.PcmSink {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val core = GaplessPlaybackCore(this, scope)

    /** Track unique `MODE_STREAM`. Null entre deux segments (recréé à la volée). */
    private var track: AudioTrack? = null

    /** SampleRate pour lequel [track] a été configuré (`-1` si aucun track). */
    private var trackSampleRate: Int = -1

    // ── AudioPlayer (délégation au cœur) ────────────────────────────────

    override fun enqueue(segment: AudioSegment) = core.enqueue(segment)

    override fun play() = core.play()

    override fun pause() = core.pause()

    override fun resume() = core.resume()

    override fun stop() = core.stop()

    override fun release() {
        core.release()
        scope.cancel()
    }

    override fun setVolume(volume: Float) = core.setVolume(volume)

    override var sampleRate: Int
        get() = core.sampleRate
        set(value) {
            core.sampleRate = value
        }

    override val state: StateFlow<PlayerState> get() = core.state

    override val pendingCount: Int get() = core.pendingCount

    // ── PcmSink (couche I/O AudioTrack, appelée sous le verrou du cœur) ──

    override fun ensureTrack(sampleRate: Int) {
        if (track != null && trackSampleRate == sampleRate && track?.state == AudioTrack.STATE_INITIALIZED) {
            return
        }
        releaseTrackInternal()

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(MIN_BUFFER_SIZE_BYTES)

        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        trackSampleRate = sampleRate
        // MODE_STREAM : le track passe en lecture avant l'arrivée des données ;
        // les écritures suivantes sont bufferisées (pattern du legacy, validé
        // sur device).
        track?.play()
    }

    override fun write(data: ByteArray, offset: Int, length: Int): Int {
        val t = track ?: return -1
        return runCatching { t.write(data, offset, length) }.getOrDefault(-1)
    }

    override fun pauseTrack() {
        track?.pause()
    }

    override fun resumeTrack() {
        track?.play()
    }

    override fun stopAndReleaseTrack() {
        releaseTrackInternal()
    }

    override fun setTrackVolume(volume: Float) {
        track?.setVolume(volume)
    }

    private fun releaseTrackInternal() {
        track?.let { t ->
            runCatching {
                if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.pause()
                t.flush()
                t.stop()
                t.release()
            }
        }
        track = null
        trackSampleRate = -1
    }

    private companion object {
        const val MIN_BUFFER_SIZE_BYTES = 4096 * 8
    }
}
