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

    fun play(segment: AudioSegment) {
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
        audioTrack.write(segment.audioData, 0, segment.audioData.size)
        audioTrack.play()

        // Liberation differee — laisse le temps a la lecture MODE_STATIC
        // de se terminer avant de liberer le AudioTrack. Approche
        // volontairement simple (Thread + sleep) pour cette tache de
        // marche a blanc uniquement ; AudioPlaybackService (Phase 5)
        // gerera ca correctement via des callbacks/coroutines.
        Thread {
            Thread.sleep(segment.durationMs + 200)
            audioTrack.stop()
            audioTrack.release()
        }.start()
    }
}
