package com.inktone.infrastructure.media

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.inktone.domain.service.AudioSegment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Remplace la lecture ad hoc de la marche à blanc (`AudioSegmentPlayer`,
 * Tâche 3.8 — `AudioTrack` jetable) par un vrai service de lecture,
 * survivant à la mise en arrière-plan. `AudioSegmentPlayer` reste utilisé
 * tel quel uniquement si un jour un besoin de preview très court (aperçu
 * d'une voix dans les réglages) justifie un chemin plus léger — pas pour
 * la lecture de publication.
 *
 * Chaque `AudioSegment` (PCM16 brut) est écrit dans un fichier WAV
 * temporaire avant d'être remis à `ExoPlayer` (voir
 * `AudioSegmentWavFile.writeToTempWavFile`, décision documentée là).
 */
@AndroidEntryPoint
class AudioPlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Joue un segment audio synthétisé : écrit dans un fichier WAV
     * temporaire (répertoire cache, nettoyé par le système), remplace
     * l'élément courant du lecteur.
     */
    fun playSegment(segment: AudioSegment) {
        val wavFile = segment.writeToTempWavFile(cacheDir)
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(wavFile)))
        player.prepare()
        player.play()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
