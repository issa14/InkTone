package com.inktone.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation

/**
 * Invite de mot de passe minimale pour l'export/import chiffré du
 * fichier local (Lot 11, tâche 11.1). Provisoire : la tâche 11.6
 * (Palier B) remplace ce dialogue par la carte « Fichier local » dédiée
 * de l'écran Configuration de synchronisation (bascule afficher/masquer
 * incluse) — ne pas dupliquer ce travail ici, seulement rester
 * fonctionnel et honnête sur l'irréversibilité en attendant.
 */
@Composable
fun BackupPasswordDialog(
    title: String,
    confirmLabel: String,
    showLossWarning: Boolean,
    onConfirm: (password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showLossWarning) {
                    Text(
                        "Ce mot de passe ne peut pas être récupéré. S'il est perdu, votre sauvegarde sera illisible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = !showLossWarning || password.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
