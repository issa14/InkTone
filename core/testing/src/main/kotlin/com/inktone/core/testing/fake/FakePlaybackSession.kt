package com.inktone.core.testing.fake

import com.inktone.domain.model.SleepTimerState
import com.inktone.domain.service.PlaybackMetadata
import com.inktone.domain.service.PlaybackSession
import com.inktone.domain.service.PlaybackSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Session de lecture TTS de test — flux mutables, commandes enregistrées.
 *
 * Les états sont pilotés par le test (`sessionStateFlow`, `metadataFlow`) et
 * les commandes seulement mémorisées : ce fake ne simule aucune synthèse, il
 * sert à vérifier QUI a été appelé et avec quoi.
 */
class FakePlaybackSession : PlaybackSession {

    val sessionStateFlow = MutableStateFlow(PlaybackSessionState.IDLE)
    val isPlayingFlow = MutableStateFlow(false)
    val currentSentenceIndexFlow = MutableStateFlow(0)
    val metadataFlow = MutableStateFlow(PlaybackMetadata())
    val sleepTimerFlow = MutableStateFlow<SleepTimerState?>(null)

    override val sessionState: StateFlow<PlaybackSessionState> get() = sessionStateFlow
    override val isPlaying: StateFlow<Boolean> get() = isPlayingFlow
    override val currentSentenceIndex: StateFlow<Int> get() = currentSentenceIndexFlow
    override val metadata: StateFlow<PlaybackMetadata> get() = metadataFlow
    override val sleepTimer: StateFlow<SleepTimerState?> get() = sleepTimerFlow

    /** Publications passées à [startNarration], dans l'ordre. */
    val startedNarrations = mutableListOf<String>()
    var togglePlayPauseCount = 0
        private set

    override fun startNarration(publicationId: String) {
        startedNarrations += publicationId
    }

    override fun togglePlayPause() {
        togglePlayPauseCount++
    }

    override fun pause() = Unit
    override fun resume() = Unit
    override fun skip(delta: Int) = Unit
    override fun stop() = Unit
    override fun setSleepTimer(minutes: Int?) = Unit
}
