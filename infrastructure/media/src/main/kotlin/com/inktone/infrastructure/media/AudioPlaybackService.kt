package com.inktone.infrastructure.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import com.inktone.domain.service.PlaybackSession
import com.inktone.domain.service.PlaybackSessionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Service foreground de la session média TTS (P1, plan polissage Pareto).
 *
 * Remplace l'ancien service fantôme (Media3 `MediaSessionService` + `ExoPlayer`
 * auquel **rien ne se connectait jamais** — la lecture réelle passe par
 * `GaplessAudioPlayer`, ADR-025) par une vraie façade : il pilote le contrat
 * domaine [PlaybackSession] (implémenté par `PlaybackOrchestrator`) et expose
 * ses commandes via une `MediaSession` système + une notification `MediaStyle`.
 *
 * Cycle de vie : démarré (en foreground) quand la lecture TTS commence, arrêté
 * quand elle s'arrête — la notification reste affichée pendant une pause réelle
 * (`Paused`), et disparaît au `stop()` (arrêt complet, libération de la
 * synthèse). La notification est la voie de contrôle en arrière-plan/écran
 * verrouillé ; l'écran Lecteur reste maître de la lecture (source de vérité
 * unique, K3).
 */
@AndroidEntryPoint
class AudioPlaybackService : Service() {

    @Inject
    lateinit var playbackSession: PlaybackSession

    @Inject
    lateinit var audioFocusController: AudioFocusController

    private var mediaSession: MediaSession? = null
    private var isForeground: Boolean = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSession(this, "InkToneTts").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    onUserCommand()
                    // `sessionState` est un flux dérivé : ne jamais enchaîner
                    // deux lectures de `.value` autour d'une mutation (la
                    // seconde serait périmée). Une lecture, une décision.
                    if (playbackSession.sessionState.value == PlaybackSessionState.PAUSED) {
                        playbackSession.resume()
                    } else {
                        playbackSession.togglePlayPause()
                    }
                }

                override fun onPause() {
                    onUserCommand()
                    playbackSession.pause()
                }

                override fun onSkipToNext() {
                    onUserCommand()
                    playbackSession.skip(1)
                }

                override fun onSkipToPrevious() {
                    onUserCommand()
                    playbackSession.skip(-1)
                }

                override fun onStop() {
                    onUserCommand()
                    playbackSession.stop()
                    stopSelf()
                }
            })
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        }
        // Le focus audio suit la vie du service : demandé quand la narration
        // commence, relâché quand elle s'arrête (P1-c).
        audioFocusController.acquire(scope)
        observeSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Les actions de la notification arrivent ici en PendingIntent ; les
        // commandes externes (bluetooth, écran verrouillé) passent par le
        // `MediaSession.Callback` ci-dessus.
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                onUserCommand()
                playbackSession.togglePlayPause()
            }
            ACTION_SKIP_NEXT -> {
                onUserCommand()
                playbackSession.skip(1)
            }
            ACTION_SKIP_PREVIOUS -> {
                onUserCommand()
                playbackSession.skip(-1)
            }
            ACTION_CANCEL_SLEEP_TIMER -> {
                onUserCommand()
                playbackSession.setSleepTimer(null)
            }
            ACTION_STOP -> {
                onUserCommand()
                playbackSession.stop()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForegroundSafe()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        audioFocusController.release()
        scope.cancel()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    /**
     * Toute commande explicite (notification, écran verrouillé, casque
     * Bluetooth) désarme la reprise automatique du focus : à partir de là,
     * l'état de lecture appartient à l'utilisateur (voir
     * [AudioInterruptionPolicy]).
     */
    private fun onUserCommand() = audioFocusController.onUserCommand()

    // ── Observation de la session ──────────────────────────────────────

    private fun observeSession() {
        scope.launch {
            playbackSession.isPlaying.collect { playing ->
                mediaSession?.setPlaybackState(buildPlaybackState(playing))
                refreshNotification()
            }
        }
        // P2-b — le décompte du minuteur de sommeil s'affiche dans la
        // notification. Rafraîchi à la minute et non à la seconde : une
        // notification réécrite chaque seconde coûte du réveil de process pour
        // une information que personne ne lit à cette précision.
        scope.launch {
            playbackSession.sleepTimer
                .map { it?.remainingMs?.let { ms -> ms / 60_000L } }
                .distinctUntilChanged()
                .collect { refreshNotification() }
        }
        scope.launch {
            playbackSession.metadata.collect { meta ->
                mediaSession?.setMetadata(
                    MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, meta.title)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, meta.author)
                        .build(),
                )
                refreshNotification()
            }
        }
    }

    private fun buildPlaybackState(playing: Boolean): PlaybackState {
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        return PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_STOP,
            )
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
    }

    // ── Notification ────────────────────────────────────────────────────

    private fun startForegroundSafe() {
        if (isForeground) return
        startForeground(NOTIFICATION_ID, buildNotification())
        isForeground = true
    }

    private fun refreshNotification() {
        if (isForeground) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    /**
     * Temps restant du minuteur de sommeil, ou `null` si aucun n'est armé.
     * Arrondi à la minute SUPÉRIEURE : afficher « 0 min » pendant les
     * dernières secondes laisserait croire que le minuteur est déjà passé.
     */
    private fun sleepTimerLabel(): String? {
        val remainingMs = playbackSession.sleepTimer.value?.remainingMs ?: return null
        val minutes = ((remainingMs + 59_999L) / 60_000L).toInt()
        return "Arrêt dans $minutes min"
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val metadata = playbackSession.metadata.value
        val playing = playbackSession.isPlaying.value

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(metadata.title ?: getString(android.R.string.unknownName))
            .setContentText(sleepTimerLabel() ?: metadata.author)
            .setContentIntent(contentIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "Précédent",
                    serviceIntent(ACTION_SKIP_PREVIOUS),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (playing) "Pause" else "Lecture",
                    serviceIntent(ACTION_PLAY_PAUSE),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "Suivant",
                    serviceIntent(ACTION_SKIP_NEXT),
                ).build(),
            )
            // `ACTION_STOP` était géré par `onStartCommand` sans qu'aucune
            // commande ne puisse l'atteindre : arrêter la narration exigeait de
            // rouvrir le Lecteur. Quatrième action (vue déployée), et même
            // intention sur le balayage de la notification.
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Arrêter",
                    serviceIntent(ACTION_STOP),
                ).build(),
            )
            .setDeleteIntent(serviceIntent(ACTION_STOP))
            .also { builder ->
                // Action présente UNIQUEMENT quand un minuteur est armé :
                // proposer « annuler » sans minuteur serait un bouton mort, et
                // en ARMER un depuis la notification demanderait de choisir une
                // durée — ce qui appartient au panneau, pas à une action.
                if (playbackSession.sleepTimer.value != null) {
                    builder.addAction(
                        Notification.Action.Builder(
                            android.R.drawable.ic_menu_close_clear_cancel,
                            "Annuler le minuteur",
                            serviceIntent(ACTION_CANCEL_SLEEP_TIMER),
                        ).build(),
                    )
                }
            }
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    // Vue compacte : les trois commandes du geste courant
                    // (phrase précédente, lecture/pause, phrase suivante).
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun serviceIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, AudioPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Lecture TTS",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Contrôle de la narration en cours (lecture, pause, phrase ±)."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "inktone.tts.playback"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.inktone.media.action.PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "com.inktone.media.action.SKIP_NEXT"
        const val ACTION_CANCEL_SLEEP_TIMER = "com.inktone.media.action.CANCEL_SLEEP_TIMER"
        const val ACTION_SKIP_PREVIOUS = "com.inktone.media.action.SKIP_PREVIOUS"
        const val ACTION_STOP = "com.inktone.media.action.STOP"
    }
}
