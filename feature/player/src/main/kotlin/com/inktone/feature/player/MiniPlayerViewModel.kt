package com.inktone.feature.player

import androidx.lifecycle.ViewModel
import com.inktone.domain.service.PlaybackMetadata
import com.inktone.domain.service.PlaybackSession
import com.inktone.domain.service.PlaybackSessionState
import com.inktone.domain.usecase.ObserveResumePublicationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    /**
     * Vrai quand cette barre ferait doublon avec la carte « Reprendre la
     * lecture » : même écran (Bibliothèque) ET même livre. Faux partout
     * ailleurs — voir [isVisible].
     */
    val isRedundantWithResumeCard: Boolean = false,
) {
    /**
     * Visible dès qu'une session est engagée — pause réelle comprise : c'est
     * précisément l'état où l'utilisateur a besoin du bouton pour reprendre.
     * Le titre conditionne l'affichage : une barre sans titre n'apprendrait
     * rien et n'aurait nulle part où ramener.
     *
     * Masquée quand elle ferait doublon avec la carte « Reprendre la
     * lecture » (voir [isRedundantWithResumeCard]) : depuis que cette carte
     * pilote la narration sans ouvrir le Lecteur, lancer la lecture depuis la
     * Bibliothèque y faisait apparaître un SECOND lecture/pause pour le même
     * livre. Le masquage est volontairement étroit : sur tout autre écran, ou
     * dès que le livre narré diffère de celui de la carte, cette barre reste
     * la seule prise sur la narration — et le seul chemin de retour vers elle.
     */
    val isVisible: Boolean
        get() = title != null && !isRedundantWithResumeCard && when (sessionState) {
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
    private val observeResumePublication: ObserveResumePublicationUseCase,
) : ViewModel() {

    val sessionState: StateFlow<PlaybackSessionState> = playbackSession.sessionState

    val isPlaying: StateFlow<Boolean> = playbackSession.isPlaying

    val metadata: StateFlow<PlaybackMetadata> = playbackSession.metadata

    /** Id du livre de reprise — celui que porte la carte de la Bibliothèque. */
    val resumePublicationId: StateFlow<String?> = observeResumePublication()
        .map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onIntent(intent: MiniPlayerIntent) {
        when (intent) {
            MiniPlayerIntent.PlayPause -> playbackSession.togglePlayPause()
            MiniPlayerIntent.PreviousSentence -> playbackSession.skip(-1)
            MiniPlayerIntent.NextSentence -> playbackSession.skip(1)
            MiniPlayerIntent.Stop -> playbackSession.stop()
        }
    }
}
