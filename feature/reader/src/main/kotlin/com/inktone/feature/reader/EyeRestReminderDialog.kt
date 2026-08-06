package com.inktone.feature.reader

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 3d.5 — popup déclenché à l'échéance du rappel de repos oculaire
 * (`UX_FLOW_DESIGN.md` §Minuteur, section 2). Distinct du minuteur de
 * sommeil TTS : celui-ci arrête la lecture, celui-là invite à faire une
 * pause sans nécessairement l'arrêter définitivement.
 *
 * Comportement audio (consigné en 3d.7) : si une lecture TTS était active,
 * `ReaderViewModel.triggerEyeRestReminder` la coupe AVANT d'afficher ce
 * popup — jamais silencieusement, le popup visible est l'avertissement.
 * Pas de doublon d'annonce TalkBack : le TTS est déjà coupé quand ce
 * `Dialog` (composant Compose standard, porte son propre focus
 * d'accessibilité) apparaît.
 */
@Composable
fun EyeRestReminderDialog(
    countdownSeconds: Int,
    onResume: () -> Unit,
    onSnooze: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onResume,
        title = { Text("Pensez à reposer vos yeux") },
        text = { Text("Cela fait un moment que vous lisez. Reprise automatique dans ${countdownSeconds}s.") },
        confirmButton = {
            TextButton(onClick = onResume) { Text("Reprendre") }
        },
        dismissButton = {
            TextButton(onClick = onSnooze) { Text("Reporter") }
        },
    )
}
