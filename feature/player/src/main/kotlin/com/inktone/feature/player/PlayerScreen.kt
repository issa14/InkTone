package com.inktone.feature.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.domain.model.VoiceProfile

/**
 * Parcours manuel complet du Palier TTS actif : play/pause/stop, vitesse,
 * sélection de voix. Même pattern que `ReaderScreen` (Tâche 4.7) : état
 * immuable (`PlayerUiState`), intents explicites (`PlayerIntent`),
 * aucune logique métier dans ce Composable — il n'affiche que ce que
 * `PlayerViewModel` (Tâche 5.5) a déjà résolu.
 */
@Composable
fun PlayerScreen(viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    PlayerContent(state, viewModel::onIntent)
}

/**
 * Composable sans etat (Tache 9.1) — separee de [PlayerScreen] pour
 * rester testable via `createAndroidComposeRule` sans `hiltViewModel()`.
 */
@Composable
internal fun PlayerContent(state: PlayerUiState, onIntent: (PlayerIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(if (state.isConnected) "Connecte a la lecture" else "Connexion en cours...")

        Row {
            Button(onClick = { onIntent(PlayerIntent.PlayPause) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (state.isPlaying) "Pause" else "Lire")
            }
            Button(onClick = { onIntent(PlayerIntent.Stop) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Stop")
            }
        }

        // Position dans la phrase courante : un segment audio = une phrase
        // (AudioPlaybackService.playSegment, un MediaItem par phrase) - la
        // progression ExoPlayer EST directement la progression de phrase,
        // pas une estimation.
        LinearProgressIndicator(
            progress = { state.sentenceProgress },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        Text("Vitesse : ${"%.2f".format(state.speed)}x")
        Slider(
            value = state.speed,
            valueRange = 0.5f..2.0f,
            onValueChange = { onIntent(PlayerIntent.ChangeSpeed(it)) },
            // Tache 9.1.1 : un Slider seul n'annonce ni son label ni sa
            // valeur courante a TalkBack.
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Vitesse de lecture, ${"%.2f".format(state.speed)}x" },
        )

        VoiceSelector(
            availableVoiceProfiles = state.availableVoiceProfiles,
            currentVoiceProfileId = state.currentVoiceProfileId,
            onVoiceSelected = { onIntent(PlayerIntent.ChangeVoice(it)) },
        )
    }
}

@Composable
private fun VoiceSelector(
    availableVoiceProfiles: List<VoiceProfile>,
    currentVoiceProfileId: String?,
    onVoiceSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = availableVoiceProfiles.firstOrNull { it.id == currentVoiceProfileId }

    Column {
        Button(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Voix : ${current?.voice ?: currentVoiceProfileId ?: "par defaut"}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            availableVoiceProfiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text("${profile.voice} (${profile.language})") },
                    onClick = {
                        onVoiceSelected(profile.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
