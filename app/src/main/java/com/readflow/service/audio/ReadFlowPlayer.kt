package com.readflow.service.audio

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Adaptateur entre le [PlaybackOrchestrator] et l'API [Player] de Media3.
 *
 * Permet d'utiliser le pipeline audio ReadFlow (ONNX → AudioTrack) comme un
 * [Player] standard pour [androidx.media3.session.MediaSessionService].
 * Les commandes (play/pause/stop) sont déléguées au [PlaybackOrchestrator].
 */
class ReadFlowPlayer(
    private val orchestrator: PlaybackOrchestrator
) : SimpleBasePlayer(Looper.getMainLooper()) {

    // ── État interne ─────────────────────────────────

    @Volatile private var playWhenReady = false
    @Volatile private var playbackState = Player.STATE_IDLE
    @Volatile private var mediaItems: List<MediaItem> = emptyList()
    @Volatile private var positionMs = 0L
    @Volatile private var totalDurationMs = 0L

    // ── API publique pour le service ─────────────────

    /** Configure le contenu en cours de lecture (titre, auteur). */
    fun setContent(title: String, artist: String = "", durationMs: Long = 0L) {
        totalDurationMs = durationMs
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .build()
        mediaItems = listOf(
            MediaItem.Builder()
                .setMediaId("chapter")
                .setMediaMetadata(metadata)
                .build()
        )
        positionMs = 0L
        invalidateState()
    }

    /** Met à jour la position estimée et la durée. */
    fun updateProgress(position: Long, duration: Long) {
        positionMs = position
        totalDurationMs = duration
        invalidateState()
    }

    /** Passe en état "ready" (prêt à jouer). */
    fun setReady(ready: Boolean) {
        if (ready && playbackState != Player.STATE_READY) {
            playbackState = Player.STATE_READY
            invalidateState()
        }
    }

    /** Passe en état "ended". */
    fun setEnded() {
        playWhenReady = false
        playbackState = Player.STATE_ENDED
        invalidateState()
    }

    /** Passe en état "idle". */
    fun setIdle() {
        playWhenReady = false
        playbackState = Player.STATE_IDLE
        positionMs = 0L
        invalidateState()
    }

    /** Rafraîchit l'état exposé à MediaSession (appelé depuis l'extérieur). */
    fun refreshState() {
        invalidateState()
    }

    // ── SimpleBasePlayer overrides ────────────────────

    override fun getState(): State {
        // Sécurité : playlist vide → forcer IDLE (évite crash SimpleBasePlayer)
        if (mediaItems.isEmpty() && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
            playbackState = Player.STATE_IDLE
        }

        // Conversion MediaItem → MediaItemData
        val playlistData = mediaItems.map { item ->
            getPlaceholderMediaItemData(item)
        }

        val builder = State.Builder()
            .setPlaylist(playlistData)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(playWhenReady, Player.COMMAND_PLAY_PAUSE)
            .setContentPositionMs(positionMs)
            .setTotalBufferedDurationMs { totalDurationMs }
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_PREPARE,
                        Player.COMMAND_STOP,
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM
                    )
                    .build()
            )

        return builder.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        if (playWhenReady) {
            if (playbackState == Player.STATE_IDLE) {
                playbackState = Player.STATE_READY
            }
            orchestrator.resume()
        } else {
            orchestrator.pause()
        }
        invalidateState()
        return Futures.immediateFuture(Unit)
    }

    override fun handlePrepare(): ListenableFuture<*> {
        playbackState = Player.STATE_READY
        invalidateState()
        return Futures.immediateFuture(Unit)
    }

    override fun handleStop(): ListenableFuture<*> {
        playWhenReady = false
        playbackState = Player.STATE_IDLE
        orchestrator.stop()
        invalidateState()
        return Futures.immediateFuture(Unit)
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        // Pas de seek supporté pour le TTS — accepter silencieusement
        this.positionMs = positionMs
        invalidateState()
        return Futures.immediateFuture(Unit)
    }

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        this.mediaItems = mediaItems.toList()
        this.positionMs = startPositionMs
        playbackState = Player.STATE_READY
        invalidateState()
        return Futures.immediateFuture(Unit)
    }
}

