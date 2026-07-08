package com.readflow.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gère le focus audio Android pour ReadFlow.
 *
 * - Appel entrant → pause automatique
 * - Notification → ducking (volume réduit)
 * - Reprise du focus → reprise automatique de la lecture
 */
@Singleton
class AudioFocusManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val orchestrator: PlaybackOrchestrator,
    private val player: GaplessAudioPlayer
) {
    companion object {
        private const val TAG = "AudioFocus"
        private const val DUCK_VOLUME = 0.3f
        private const val FULL_VOLUME = 1.0f
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        Log.d(TAG, "Focus change: ${focusChangeToString(change)}")
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Perte permanente (appel, autre app média) → pause
                abandonFocus()
                orchestrator.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Perte temporaire (notif courte) → pause
                orchestrator.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Perte temporaire mais peut réduire le volume → ducking
                player.setVolume(DUCK_VOLUME)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Focus regagné → reprendre
                player.setVolume(FULL_VOLUME)
                if (orchestrator.state.value is PlaybackOrchestrator.State.Paused) {
                    orchestrator.resume()
                }
            }
        }
    }

    /** Demande le focus audio. Retourne true si accordé. */
    fun requestFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        Log.d(TAG, "Focus demandé → ${if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "OK" else "REFUSÉ"}")
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /** Abandonne le focus audio. */
    fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Le focus est automatiquement abandonné via le AudioFocusRequest
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        Log.d(TAG, "Focus abandonné")
    }

    private fun focusChangeToString(change: Int): String = when (change) {
        AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
        AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
        else -> "UNKNOWN($change)"
    }
}
