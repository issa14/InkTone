package com.inktone.feature.reader

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.inktone.domain.service.AudioSegment
import javax.inject.Inject

/**
 * Lecture minimale pour la marche à blanc (Tâche 3.8) — un AudioTrack
 * par segment, aucune file d'attente, aucune lecture en arrière-plan.
 * AudioPlaybackService (Phase 5) remplacera ceci pour l'usage réel.
 */
class AudioSegmentPlayer @Inject constructor() {

    private var currentTrack: AudioTrack? = null

    fun play(segment: AudioSegment) {
        stop()
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
        audioTrack.write(segment.audioData, 0, segment.audioData.size)
        audioTrack.play()

        // Libération différée — ne nettoie QUE si ce track est toujours
        // le currentTrack (évite de stopper un track déjà remplacé par
        // l'auto-advance TTS : A.1 enchaîne les phrases, le stop() du
        // prochain play() libère déjà l'ancien track).
        Thread {
            Thread.sleep(segment.durationMs + 200)
            if (currentTrack === audioTrack) {
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: IllegalStateException) {
                    // déjà libéré, rien à faire
                }
            }
        }.start()
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
