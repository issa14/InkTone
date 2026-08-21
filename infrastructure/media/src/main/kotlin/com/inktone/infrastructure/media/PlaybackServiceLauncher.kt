package com.inktone.infrastructure.media

import android.content.Context
import android.content.Intent
import com.inktone.domain.service.PlaybackSession
import com.inktone.domain.service.PlaybackSessionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Déclencheur du service foreground de la notification média (P1).
 *
 * Observe l'état de la session [PlaybackSession] et démarre/arrête
 * `AudioPlaybackService` en conséquence — découplé du `ReaderViewModel`
 * (qui ne connaît ni `Context` ni le service, Blueprint §12.4) : la survie
 * de la narration en arrière-plan est pilotée par l'état, pas par l'écran.
 *
 * Règle :
 * - `PLAYING`/`BUFFERING` → démarrer le service (foreground, notification) ;
 * - `PAUSED` → conserver la notification (icône « lecture ») ;
 * - `IDLE`/`ERROR` → arrêter le service, **après un court délai** pour
 *   absorber l'`Idle` transitoire du chapitre qui s'enchaîne (auto-avance) —
 *   sans quoi la notification clignoterait à chaque fin de chapitre.
 *
 * Activé une seule fois depuis `InkToneApplication.onCreate()` via [start].
 */
@Singleton
class PlaybackServiceLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackSession: PlaybackSession,
) {

    private var stopJob: Job? = null

    fun start(scope: CoroutineScope) {
        scope.launch {
            playbackSession.sessionState.collect { state ->
                when (state) {
                    PlaybackSessionState.PLAYING, PlaybackSessionState.BUFFERING -> {
                        stopJob?.cancel()
                        stopJob = null
                        startService()
                    }
                    PlaybackSessionState.PAUSED -> {
                        // Pause réelle : la notification reste (icône « lecture »).
                        stopJob?.cancel()
                        stopJob = null
                    }
                    PlaybackSessionState.IDLE, PlaybackSessionState.ERROR -> {
                        if (stopJob == null) {
                            stopJob = scope.launch {
                                delay(STOP_DEBOUNCE_MS)
                                stopService()
                                stopJob = null
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startService() {
        context.startForegroundService(Intent(context, AudioPlaybackService::class.java))
    }

    private fun stopService() {
        context.stopService(Intent(context, AudioPlaybackService::class.java))
    }

    private companion object {
        /**
         * Délai avant l'arrêt du service sur `IDLE`, pour absorber l'`Idle`
         * transitoire de l'auto-avance de chapitre (le `play()` suivant arrive
         * en ~50 ms et annule l'arrêt). Sans ce délai, la notification
         * clignoterait à chaque transition de chapitre.
         */
        const val STOP_DEBOUNCE_MS = 400L
    }
}
