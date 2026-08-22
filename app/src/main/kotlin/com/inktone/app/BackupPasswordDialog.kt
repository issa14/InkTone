package com.inktone.app

import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Invite de mot de passe pour l'export/import chiffré du fichier local
 * (Lot 11, tâche 11.1). Décision actée à la tâche 11.6 : reste ici
 * plutôt que déplacée vers une carte dédiée de l'écran Configuration de
 * synchronisation — la carte « Fichier local » de cet écran pointe vers
 * Réglages au lieu de dupliquer ce dialogue une seconde fois.
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
    var isPasswordVisible by remember { mutableStateOf(false) }

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
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            AppIcon(
                                if (isPasswordVisible) AppSymbol.VisibilityOff else AppSymbol.Visibility,
                                contentDescription = if (isPasswordVisible) "Masquer le mot de passe" else "Afficher le mot de passe",
                            )
                        }
                    },
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
