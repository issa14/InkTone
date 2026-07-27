package com.inktone.feature.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val speed: Float = 1.0f,
    val currentVoiceProfileId: String? = null,
    val isConnected: Boolean = false,
)

sealed interface PlayerIntent {
    data object PlayPause : PlayerIntent
    data object Stop : PlayerIntent
    data class ChangeSpeed(val speed: Float) : PlayerIntent
    data class ChangeVoice(val voiceProfileId: String) : PlayerIntent
}

/**
 * Pilote `AudioPlaybackService` (Tâche 5.4, `infrastructure/media`) via
 * `MediaController` — jamais de dépendance de compilation directe sur ce
 * module (Blueprint §12.4, `feature` ne dépend jamais d'`infrastructure`) :
 * la connexion se fait par `ComponentName` construit à partir du nom de
 * classe (chaîne), qui doit rester synchronisé manuellement avec le
 * service réel (voir [AUDIO_PLAYBACK_SERVICE_CLASS_NAME]).
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _state.value = _state.value.copy(speed = playbackParameters.speed)
        }
    }

    init {
        connectToPlaybackService()
    }

    private fun connectToPlaybackService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context.packageName, AUDIO_PLAYBACK_SERVICE_CLASS_NAME),
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                controller = controllerFuture.get().also { it.addListener(playerListener) }
                _state.value = _state.value.copy(isConnected = true, isPlaying = controller?.isPlaying == true)
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun onIntent(intent: PlayerIntent) {
        when (intent) {
            is PlayerIntent.PlayPause -> togglePlayPause()
            is PlayerIntent.Stop -> stop()
            is PlayerIntent.ChangeSpeed -> changeSpeed(intent.speed)
            is PlayerIntent.ChangeVoice -> changeVoice(intent.voiceProfileId)
        }
    }

    private fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    private fun stop() {
        controller?.stop()
    }

    private fun changeSpeed(speed: Float) {
        // Rappel Blueprint §8.9 : un changement de vitesse doit recalculer
        // les timestamps EXACTEMENT, pas les approximer. Pour le Palier 1
        // (Android natif), le moteur resynthetise deja a la bonne vitesse
        // (voiceProfile.speed, Tache 3.x) - piloter ExoPlayer ici ne fait
        // que suivre la vitesse de lecture du flux deja genere. Pour le
        // Palier 2 (Sherpa-ONNX), le meme principe s'applique via son
        // propre parametre de vitesse a la synthese (Tache 5.1) ; ce
        // ViewModel ne fait qu'exposer l'intent, la resynthese elle-meme
        // reste la responsabilite de la couche TTS, pas de ce controleur
        // de lecture.
        controller?.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
    }

    private fun changeVoice(voiceProfileId: String) {
        _state.value = _state.value.copy(currentVoiceProfileId = voiceProfileId)
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        super.onCleared()
    }

    private companion object {
        const val AUDIO_PLAYBACK_SERVICE_CLASS_NAME = "com.inktone.infrastructure.media.AudioPlaybackService"
    }
}
