package com.inktone.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.inktone.core.designsystem.Motion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import com.inktone.core.designsystem.reducedMotionDuration
import com.inktone.domain.service.PlaybackSessionState

/**
 * Barre de lecture persistante affichée sous le contenu de tous les écrans
 * hors Lecteur (P2, plan polissage Pareto).
 *
 * Depuis le palier P1-d, quitter le Lecteur ne coupe plus la narration : sans
 * cette barre, la voix continuerait sans qu'aucun contrôle ne soit visible
 * dans l'application — il faudrait sortir vers le volet de notifications pour
 * la mettre en pause. Elle rend visible ce que la session sait déjà, et
 * n'introduit aucun second état (K3).
 *
 * Un appui sur la barre ramène au Lecteur, sur le livre réellement narré
 * (`PlaybackMetadata.publicationId`) — jamais sur le dernier livre ouvert, qui
 * peut être un autre.
 */
@Composable
fun MiniPlayerBar(
    onOpenReader: (publicationId: String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Vrai sur la Bibliothèque, seul écran à porter la carte « Reprendre la
     * lecture ». C'est l'appelant qui le sait (`InkToneNavHost` connaît la
     * destination) ; ce composable ne devine pas où il est affiché.
     */
    isResumeCardVisible: Boolean = false,
    viewModel: MiniPlayerViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val metadata by viewModel.metadata.collectAsStateWithLifecycle()
    val resumePublicationId by viewModel.resumePublicationId.collectAsStateWithLifecycle()

    MiniPlayerContent(
        state = MiniPlayerUiState(
            sessionState = sessionState,
            isPlaying = isPlaying,
            title = metadata.title,
            author = metadata.author,
            publicationId = metadata.publicationId,
            // Doublon seulement si les deux conditions tiennent : la carte est
            // à l'écran ET elle porte le livre narré.
            isRedundantWithResumeCard = isResumeCardVisible &&
                metadata.publicationId != null &&
                metadata.publicationId == resumePublicationId,
        ),
        onIntent = viewModel::onIntent,
        onOpenReader = onOpenReader,
        modifier = modifier,
    )
}

/**
 * Contenu sans état du mini-lecteur — séparé de [MiniPlayerBar] sur le modèle
 * établi ailleurs dans le dépôt, pour que le comportement (visibilité,
 * libellés, commandes émises) soit testable sans graphe Hilt ni session réelle.
 */
@Composable
internal fun MiniPlayerContent(
    state: MiniPlayerUiState,
    onIntent: (MiniPlayerIntent) -> Unit,
    onOpenReader: (publicationId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessionState = state.sessionState
    val isPlaying = state.isPlaying
    val isVisible = state.isVisible
    // P5 — durée du système de design plutôt qu'une valeur locale ; le
    // mouvement réduit reste appliqué, désormais par construction.
    val spec = Motion.tween<Float>()
    val slideSpec = Motion.tween<androidx.compose.ui.unit.IntOffset>()

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(slideSpec) { it } + fadeIn(spec),
        exit = slideOutVertically(slideSpec) { it } + fadeOut(spec),
        modifier = modifier,
    ) {
        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    // Edge-to-edge : cette barre est le dernier élément de la
                    // colonne racine, hors de tout `Scaffold` — rien ne lui
                    // applique l'inset du bas. Sans ce padding, ses commandes
                    // passeraient sous la barre de navigation système. Il est
                    // posé sur le contenu et non sur la `Surface`, pour que le
                    // fond, lui, continue de peindre jusqu'au bord.
                    .navigationBarsPadding()
                    .clickable(enabled = state.publicationId != null) {
                        state.publicationId?.let(onOpenReader)
                    }
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = state.author
                        ?: if (sessionState == PlaybackSessionState.PAUSED) "En pause" else "Narration en cours"
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onIntent(MiniPlayerIntent.PreviousSentence) }) {
                    AppIcon(AppSymbol.SentencePrevious, contentDescription = "Phrase précédente")
                }
                IconButton(onClick = { onIntent(MiniPlayerIntent.PlayPause) }) {
                    AppIcon(
                        symbol = if (isPlaying) AppSymbol.Pause else AppSymbol.Play,
                        contentDescription = if (isPlaying) "Mettre en pause" else "Reprendre la lecture",
                    )
                }
                IconButton(onClick = { onIntent(MiniPlayerIntent.NextSentence) }) {
                    AppIcon(AppSymbol.SentenceNext, contentDescription = "Phrase suivante")
                }
                IconButton(onClick = { onIntent(MiniPlayerIntent.Stop) }) {
                    AppIcon(AppSymbol.Close, contentDescription = "Arrêter la narration")
                }
            }
        }
    }
}
