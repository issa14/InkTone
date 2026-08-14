package com.inktone.feature.reader

import com.inktone.domain.service.AudioPlayer
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlaybackPosition
import com.inktone.domain.service.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Fake [AudioPlayer] partagé par les tests JVM du module reader (Tâches
 * 3.4 et 4.1). Enregistre les segments enfilés et les appels, sans aucune
 * dépendance Android — le vrai lecteur ([GaplessAudioPlayer]) vit en
 * `infrastructure/media` et ne se teste qu'en instrumenté.
 */
class FakeAudioPlayer : AudioPlayer {
    val enqueued = CopyOnWriteArrayList<AudioSegment>()
    val events = CopyOnWriteArrayList<String>()
    override var sampleRate: Int = 22_050

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    override val state: StateFlow<PlayerState> = _state.asStateFlow()
    override val pendingCount: Int get() = enqueued.size

    private val _playbackPosition = MutableStateFlow<PlaybackPosition>(INVALID_POSITION)
    override val playbackPosition: StateFlow<PlaybackPosition> = _playbackPosition.asStateFlow()

    /** Règle la position que le fake expose (tests de surlignage par position). */
    fun emitPosition(position: PlaybackPosition) {
        _playbackPosition.value = position
    }

    override fun enqueue(segment: AudioSegment) {
        enqueued.add(segment)
    }

    override fun play() {
        events.add("play")
        _state.value = PlayerState.Playing
    }

    override fun pause() {
        events.add("pause")
        _state.value = PlayerState.Paused
    }

    override fun resume() {
        events.add("resume")
        _state.value = PlayerState.Playing
    }

    override fun stop() {
        events.add("stop")
        enqueued.clear()
        _state.value = PlayerState.Stopped
    }

    override fun release() {
        events.add("release")
    }

    override fun setVolume(volume: Float) = Unit

    private companion object {
        val INVALID_POSITION = PlaybackPosition(playedFrame = 0, sampleRate = 0, timestampNanos = null, valid = false)
    }
}
