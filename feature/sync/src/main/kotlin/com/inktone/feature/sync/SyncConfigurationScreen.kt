package com.inktone.feature.sync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.AppIcons
import com.inktone.domain.model.SyncUiState

/**
 * Écran Configuration de synchronisation (tâche 11.6). Avec Drive,
 * l'autorisation réussie **est** la validation — pas d'URL à saisir ni
 * de test séparé, contrairement à ce que sera WebDAV plus tard.
 *
 * Carte « Fichier local » : pointe vers Réglages plutôt que de
 * réimplémenter une seconde fois le mot de passe E2EE/l'export/import
 * (tâche 11.1) — un seul chemin d'implémentation, pas deux qui
 * divergeraient (contrainte explicite du plan).
 *
 * Transition en fondu vers l'écran Opérationnel (tâche 11.8, palier C)
 * volontairement absente ici : cet écran n'existe pas encore, il n'y a
 * rien vers quoi transitionner — à ajouter avec `AnimatedContent` au
 * palier C, pas simulé ici.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncConfigurationScreen(
    viewModel: SyncViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenLocalBackup: () -> Unit = {},
    isGoogleAuthenticating: Boolean = false,
    isGoogleConfigured: Boolean = true,
    onConnectGoogle: () -> Unit = {},
    onDisconnectGoogle: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuration Sync") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    val account = (state.syncUiState as? SyncUiState.Configured)?.account
                    TextButton(onClick = onBack, enabled = account != null) { Text("Enregistrer") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GoogleDriveCard(
                syncUiState = state.syncUiState,
                isAuthenticating = isGoogleAuthenticating,
                isConfigured = isGoogleConfigured,
                onConnect = onConnectGoogle,
                onDisconnect = onDisconnectGoogle,
            )
            WebDavCard()
            LocalBackupCard(onOpenLocalBackup)
        }
    }
}

@Composable
private fun GoogleDriveCard(
    syncUiState: SyncUiState,
    isAuthenticating: Boolean,
    isConfigured: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val account = when (syncUiState) {
        is SyncUiState.Configured -> syncUiState.account
        is SyncUiState.Syncing -> syncUiState.account
        else -> null
    }
    val isActive = account != null

    Card(
        colors = if (isActive) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            )
        } else {
            CardDefaults.cardColors()
        },
        border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isActive) AppIcons.CloudConnected else AppIcons.CloudDisconnected,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text("Google Drive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                StatusBadge(isActive)
            }
            if (isActive && account != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.CloudConnected, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        // Portée volontairement limitée à drive.appdata (décision
                        // actée) : aucune adresse e-mail réelle n'est disponible
                        // sans étendre la portée OAuth — écart déclaré plutôt
                        // qu'un e-mail inventé ou une portée élargie en douce.
                        Text(account.accountLabel, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Connecté",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Déconnecter") }
            } else {
                Spacer(Modifier.height(12.dp))
                if (!isConfigured) {
                    // Absence de configuration OAuth rendue explicite plutôt
                    // que silencieuse (tâche 11.4) : clientId/redirectScheme
                    // sont un prérequis hors périmètre de Claude Code, pris
                    // en charge par Issa (docs/execution/LOT_11_SYNCHRONISATION.md).
                    Text(
                        "Configuration Google absente (clientId non renseigné) — le bouton restera inactif tant qu'elle n'est pas fournie.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(onClick = onConnect, enabled = !isAuthenticating && isConfigured, modifier = Modifier.fillMaxWidth()) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("Se connecter avec Google")
                }
            }
        }
    }
}

@Composable
private fun WebDavCard() {
    // Grisage permanent, pas conditionnel à l'autre fournisseur actif
    // (contrairement à la cible) : aucune implémentation n'existe encore
    // (WebDAV arrive après ce lot). Un bouton désactivé qui explique
    // pourquoi n'est pas un contrôle décoratif — à consigner.
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "WebDAV",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Bientôt disponible",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocalBackupCard(onOpenLocalBackup: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fichier local", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "AUTONOME",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Export/import chiffré, indépendant du cloud — géré depuis Réglages.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenLocalBackup, modifier = Modifier.fillMaxWidth()) { Text("Gérer dans Réglages") }
        }
    }
}

@Composable
private fun StatusBadge(isActive: Boolean) {
    Text(
        if (isActive) "ACTIF" else "INACTIF",
        style = MaterialTheme.typography.labelSmall,
        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
