package com.inktone.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inktone.core.designsystem.InkToneShapes

/**
 * B.3 — Panneau de contrôle TTS accessible depuis le Reader.
 * Navigation phrase à phrase, play/pause/stop, vitesse, minuteur.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderTtsPanel(
    isPlaying: Boolean,
    currentSentenceIndex: Int,
    totalSentences: Int,
    currentSpeed: Float,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSleepTimer: (Int?) -> Unit,
    currentSleepTimerMinutes: Int?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                "Contrôle vocal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            // ── Navigation phrase ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPreviousSentence) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Phrase précédente")
                }
                Text(
                    "Phrase ${currentSentenceIndex + 1} / $totalSentences",
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = onNextSentence) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Phrase suivante")
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Play/Pause/Stop ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = InkToneShapes.large,
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Lire",
                        tint = MaterialTheme.colorScheme.surface,
                    )
                }
                Spacer(Modifier.width(24.dp))
                IconButton(onClick = onStop, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "Arrêter",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Vitesse ──
            Text("Vitesse (${"%.1f".format(currentSpeed)}×)", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = currentSpeed,
                onValueChange = onSpeedChange,
                valueRange = 0.5f..3.0f,
                steps = 9,
            )

            Spacer(Modifier.height(20.dp))

            // ── Minuteur de sommeil ──
            Text("Veille", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            val sleepOptions = listOf(15, 30, 45, 60)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sleepOptions.forEach { minutes ->
                    FilterChip(
                        selected = currentSleepTimerMinutes == minutes,
                        onClick = { onSleepTimer(minutes) },
                        label = { Text("${minutes} min") },
                    )
                }
                FilterChip(
                    selected = currentSleepTimerMinutes == null,
                    onClick = { onSleepTimer(null) },
                    label = { Text("Off") },
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
