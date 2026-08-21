package com.inktone.infrastructure.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.inktone.domain.service.PlaybackSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Couche I/O du focus audio (P1-c, plan polissage Pareto) : traduit les
 * signaux `AudioManager` en actions sur la [PlaybackSession], selon les règles
 * de [AudioInterruptionPolicy] (qui, elle, ne connaît pas Android).
 *
 * Avant ce composant, la narration ne se taisait **jamais** : ni pour un appel
 * entrant, ni pour une autre application, ni quand le casque était débranché —
 * la voix repartait alors dans le haut-parleur. `GaplessAudioPlayer` construit
 * son `AudioTrack` sans jamais demander de focus (`GaplessAudioPlayer.ensureTrack`).
 *
 * Le focus est demandé en `AUDIOFOCUS_GAIN` avec des attributs `USAGE_MEDIA` /
 * `CONTENT_TYPE_SPEECH` — cohérents avec ceux de l'`AudioTrack` réel — et
 * `setWillPauseWhenDucked(true)` : le système ne tente pas d'atténuer une voix,
 * il nous laisse pauser (voir la justification dans [AudioInterruptionPolicy]).
 *
 * Possédé par [AudioPlaybackService] : acquis quand le service de lecture
 * démarre, relâché quand il meurt. Aucun autre point du code ne demande de
 * focus — un second demandeur créerait exactement la divergence d'état que K3
 * interdit.
 */
@Singleton
class AudioFocusController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackSession: PlaybackSession,
) {

    private val audioManager: AudioManager
        get() = context.getSystemService(AudioManager::class.java)

    private val policy = AudioInterruptionPolicy()

    private var focusRequest: AudioFocusRequest? = null
    private var noisyReceiver: BroadcastReceiver? = null
    private var stateJob: Job? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val interruption = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> AudioInterruption.FOCUS_GAINED
            AudioManager.AUDIOFOCUS_LOSS -> AudioInterruption.FOCUS_LOST
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> AudioInterruption.FOCUS_LOST_TRANSIENT
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                AudioInterruption.FOCUS_LOST_TRANSIENT_CAN_DUCK
            else -> return@OnAudioFocusChangeListener
        }
        apply(policy.onInterruption(interruption, playbackSession.sessionState.value))
    }

    /**
     * Demande le focus audio, écoute le débranchement de casque, et surveille
     * l'état de session pour appliquer une pause différée (cas `BUFFERING`).
     *
     * Un refus du système (appel en cours, par exemple) est traité comme une
     * perte définitive : la narration se met en pause plutôt que de parler
     * par-dessus.
     */
    fun acquire(scope: CoroutineScope) {
        if (focusRequest != null) return

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request

        registerNoisyReceiver()
        stateJob = scope.launch {
            playbackSession.sessionState.collect { state ->
                apply(policy.onSessionStateChanged(state))
            }
        }

        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            policy.onFocusDenied()
        }
    }

    /** Relâche le focus et cesse d'écouter les interruptions. */
    fun release() {
        stateJob?.cancel()
        stateJob = null
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        unregisterNoisyReceiver()
        policy.onUserCommand()
    }

    /**
     * À appeler quand l'utilisateur commande lui-même la lecture : la
     * politique renonce alors à toute reprise automatique ultérieure.
     */
    fun onUserCommand() = policy.onUserCommand()

    private fun apply(action: InterruptionAction) {
        when (action) {
            InterruptionAction.PAUSE -> playbackSession.pause()
            InterruptionAction.RESUME -> playbackSession.resume()
            InterruptionAction.NONE -> Unit
        }
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
                apply(policy.onInterruption(AudioInterruption.BECAME_NOISY, playbackSession.sessionState.value))
            }
        }
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        // targetSdk 33+ impose de déclarer l'exposition du receiver ; ce
        // diffuseur est un signal système, jamais destiné aux autres apps.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        noisyReceiver = receiver
    }

    private fun unregisterNoisyReceiver() {
        noisyReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        noisyReceiver = null
    }
}
