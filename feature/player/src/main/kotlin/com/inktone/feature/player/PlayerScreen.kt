package com.inktone.feature.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

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

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(if (state.isConnected) "Connecte a la lecture" else "Connexion en cours...")

        Row {
            Button(onClick = { viewModel.onIntent(PlayerIntent.PlayPause) }) {
                Text(if (state.isPlaying) "Pause" else "Lire")
            }
            Button(onClick = { viewModel.onIntent(PlayerIntent.Stop) }) {
                Text("Stop")
            }
        }

        Text("Vitesse : ${"%.2f".format(state.speed)}x")
        Slider(
            value = state.speed,
            valueRange = 0.5f..2.0f,
            onValueChange = { viewModel.onIntent(PlayerIntent.ChangeSpeed(it)) },
        )

        Text("Voix : ${state.currentVoiceProfileId ?: "par defaut"}")
    }
}
