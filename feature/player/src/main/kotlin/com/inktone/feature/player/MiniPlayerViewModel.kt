package com.inktone.feature.player

import androidx.lifecycle.ViewModel
import com.inktone.domain.service.PlaybackMetadata
import com.inktone.domain.service.PlaybackSession
import com.inktone.domain.service.PlaybackSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * État affiché par le mini-lecteur. Entièrement dérivé de la session : aucun
 * champ n'est maintenu à la main (K3, une seule source de vérité).
 */
data class MiniPlayerUiState(
    val sessionState: PlaybackSessionState = PlaybackSessionState.IDLE,
    val isPlaying: Boolean = false,
    val title: String? = null,
    val author: String? = null,
    val publicationId: String? = null,
) {
    /**
     * Visible dès qu'une session est engagée — pause réelle comprise : c'est
     * précisément l'état où l'utilisateur a besoin du bouton pour reprendre.
     * Le titre conditionne l'affichage : une barre sans titre n'apprendrait
     * rien et n'aurait nulle part où ramener.
     */
    val isVisible: Boolean
        get() = title != null && when (sessionState) {
            PlaybackSessionState.PLAYING,
            PlaybackSessionState.BUFFERING,
            PlaybackSessionState.PAUSED,
            -> true
            PlaybackSessionState.IDLE, PlaybackSessionState.ERROR -> false
        }
}

/** Commandes du mini-lecteur. */
sealed interface MiniPlayerIntent {
    data object PlayPause : MiniPlayerIntent
    data object PreviousSentence : MiniPlayerIntent
    data object NextSentence : MiniPlayerIntent
    data object Stop : MiniPlayerIntent
}

/**
 * Mini-lecteur persistant (P2, plan polissage Pareto).
 *
 * Remplace l'ancien `PlayerViewModel`, **code mort** depuis son écriture :
 * aucune route ne référençait `PlayerScreen`, et il pilotait par
 * `MediaController` un `AudioPlaybackService` qui ne jouait rien (constat §1
 * du plan). Il n'en reste rien à conserver : la session est désormais un
 * contrat domaine ([PlaybackSession]) exposé par Hilt, donc consommable
 * directement — sans `MediaController`, sans `ComponentName` reconstruit
 * depuis un nom de classe en chaîne à resynchroniser à la main.
 *
 * Ne détient aucun état propre : tout dérive de la session, seule source de
 * vérité (K3). Une pause venue de la notification, de l'écran verrouillé ou du
 * Lecteur se reflète ici sans une ligne de synchronisation.
 */
@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val playbackSession: PlaybackSession,
) : ViewModel() {

    val sessionState: StateFlow<PlaybackSessionState> = playbackSession.sessionState

    val isPlaying: StateFlow<Boolean> = playbackSession.isPlaying

    val metadata: StateFlow<PlaybackMetadata> = playbackSession.metadata

    fun onIntent(intent: MiniPlayerIntent) {
        when (intent) {
            MiniPlayerIntent.PlayPause -> playbackSession.togglePlayPause()
            MiniPlayerIntent.PreviousSentence -> playbackSession.skip(-1)
            MiniPlayerIntent.NextSentence -> playbackSession.skip(1)
            MiniPlayerIntent.Stop -> playbackSession.stop()
        }
    }
}
