package com.inktone.feature.reader

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.inktone.domain.service.AudioSegment
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Lecture d'un segment via [AudioTrack] (Tâche 3.8). Correctif Lot 14 :
 * [play] est désormais **bloquant** — il suspend jusqu'à la fin RÉELLE de
 * l'audio (notification de position AudioTrack), au lieu de retourner
 * immédiatement pendant qu'un thread détaché libérait le track. La boucle de
 * lecture pilotait par des `delay()` de durées de mots (dont la somme < durée
 * audio réelle) et coupait l'audio en cours — mots et phrases sautés.
 * Le pipeline gapless complet (file d'attente, MODE_STREAM) reste le Lot 15.
 */
class AudioSegmentPlayer @Inject constructor() {

    private var currentTrack: AudioTrack? = null

    /** Joue le segment et suspend jusqu'à la fin réelle de l'audio. Retourne la durée jouée en ms. */
    suspend fun play(segment: AudioSegment): Long = suspendCancellableCoroutine { cont ->
        stop()
        val frameCount = segment.audioData.size / 2
        if (frameCount == 0) {
            cont.resume(0L)
            return@suspendCancellableCoroutine
        }
        val minBufferSize = AudioTrack.getMinBufferSize(
            segment.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(segment.sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
            maxOf(minBufferSize, segment.audioData.size),
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        currentTrack = audioTrack

        audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack) {
                currentTrack = null
                runCatching { track.stop() }
                runCatching { track.release() }
                if (cont.isActive) cont.resume((frameCount * 1000L) / segment.sampleRate)
            }

            override fun onPeriodicNotification(track: AudioTrack) {}
        })
        // Notification à l'avant-dernière frame : la doc exige une position
        // strictement inférieure à la taille du buffer.
        audioTrack.setNotificationMarkerPosition((frameCount - 1).coerceAtLeast(1))

        audioTrack.write(segment.audioData, 0, segment.audioData.size)
        audioTrack.play()

        cont.invokeOnCancellation {
            currentTrack = null
            runCatching { audioTrack.stop() }
            runCatching { audioTrack.release() }
        }
    }

    /**
     * A.2 — Arrêt propre du segment en cours. Appelé depuis
     * [ReaderViewModel.onCleared] pour éviter qu'un audio survive
     * à la destruction du ViewModel.
     */
    fun stop() {
        currentTrack?.let { track ->
            try {
                track.stop()
                track.release()
            } catch (_: IllegalStateException) {
                // déjà libéré, rien à faire
            }
        }
        currentTrack = null
    }
}
