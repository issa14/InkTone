package com.inktone.feature.sync

import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.text.format.DateUtils
import com.inktone.core.designsystem.AppIcons
import com.inktone.core.designsystem.StatusBarColorEffect
import com.inktone.domain.model.DeviceFleetEntry
import com.inktone.domain.model.SyncActivityEvent
import com.inktone.domain.model.SyncActivityEventType
import com.inktone.domain.model.SyncProviderId
import com.inktone.domain.model.SyncUiState

/**
 * Écran Synchronisation (drawer, b5 ; tâches 11.6/11.8) — module piloté
 * par [SyncUiState], basculant entre trois vues : chargement
 * (`Authenticating`), Configuration (`Unconfigured`/pas de compte) et
 * Opérationnel/Dashboard (`Configured`/`Syncing`). `manualShowConfig`
 * force temporairement la vue Configuration même compte lié — c'est ce
 * que fait le bouton « Gérer » du Dashboard, sans navigation réelle
 * (même route, même écran).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncConfigurationScreen(
    viewModel: SyncViewModel = hiltViewModel(),
    // Lot 18 — la flèche de retour cède la place au hamburger du drawer
    // partagé ; [onBack] reste utilisé par le bouton « Enregistrer », qui
    // quitte l'écran une fois la configuration validée (comportement
    // inchangé, ce n'est pas l'icône de navigation).
    onMenuClick: () -> Unit = {},
    onBack: () -> Unit = {},
    onOpenLocalBackup: () -> Unit = {},
    isGoogleAuthenticating: Boolean = false,
    isGoogleConfigured: Boolean = true,
    onConnectGoogle: () -> Unit = {},
    onDisconnectGoogle: () -> Unit = {},
    // Lot 19 — carte WebDAV fonctionnelle
    isWebDavConnecting: Boolean = false,
    onConnectWebDav: (url: String, username: String, password: String) -> Unit = { _, _, _ -> },
    onDisconnectWebDav: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var manualShowConfig by remember { mutableStateOf(false) }

    val isConfigured = state.syncUiState is SyncUiState.Configured || state.syncUiState is SyncUiState.Syncing
    val showOperational = isConfigured && !manualShowConfig

    // Etend la couleur de la barre du haut a la barre de statut Android :
    // sans cela, le bandeau systeme garde le creme fige de `themes.xml`
    // au-dessus d'une TopAppBar `primary` (voir StatusBarColorEffect).
    StatusBarColorEffect(MaterialTheme.colorScheme.primary)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showOperational) "Synchronisation" else "Configuration Sync") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        AppIcon(AppSymbol.Menu, contentDescription = "Ouvrir le menu")
                    }
                },
                actions = {
                    // Lot 18 — la barre est peinte en `primary` : sans
                    // couleur explicite, un TextButton rendrait son
                    // libellé en `primary` sur `primary`, donc invisible.
                    val barButtonColors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
                    )
                    if (showOperational) {
                        TextButton(onClick = { manualShowConfig = true }, colors = barButtonColors) { Text("Gérer") }
                    } else {
                        TextButton(
                            onClick = { manualShowConfig = false; onBack() },
                            enabled = isConfigured,
                            colors = barButtonColors,
                        ) { Text("Enregistrer") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = showOperational,
            transitionSpec = {
                if (state.reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "sync-configuration-operational",
            modifier = Modifier.padding(innerPadding),
        ) { operational ->
            when {
                state.syncUiState is SyncUiState.Authenticating -> AuthenticatingContent()
                operational -> SyncOperationalContent(
                    state = state,
                    onIntent = viewModel::onIntent,
                )
                else -> SyncConfigurationContent(
                    state = state,
                    isGoogleAuthenticating = isGoogleAuthenticating,
                    isGoogleConfigured = isGoogleConfigured,
                    onConnectGoogle = onConnectGoogle,
                    onDisconnectGoogle = { manualShowConfig = false; onDisconnectGoogle() },
                    onOpenLocalBackup = onOpenLocalBackup,
                    isWebDavConnecting = isWebDavConnecting,
                    onConnectWebDav = onConnectWebDav,
                    onDisconnectWebDav = onDisconnectWebDav,
                )
            }
        }
    }
}

@Composable
private fun AuthenticatingContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Connexion au service cloud...")
    }
}

@Composable
private fun SyncConfigurationContent(
    state: SyncScreenState,
    isGoogleAuthenticating: Boolean,
    isGoogleConfigured: Boolean,
    onConnectGoogle: () -> Unit,
    onDisconnectGoogle: () -> Unit,
    onOpenLocalBackup: () -> Unit,
    isWebDavConnecting: Boolean,
    onConnectWebDav: (url: String, username: String, password: String) -> Unit,
    onDisconnectWebDav: () -> Unit,
) {
    val account = (state.syncUiState as? SyncUiState.Configured)?.account
        ?: (state.syncUiState as? SyncUiState.Syncing)?.account
    val isWebDavActive = account?.provider == SyncProviderId.WEBDAV
    val isGoogleActive = account?.provider == SyncProviderId.GOOGLE_DRIVE

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GoogleDriveCard(
            syncUiState = state.syncUiState,
            isAuthenticating = isGoogleAuthenticating,
            isConfigured = isGoogleConfigured,
            isOtherProviderActive = isWebDavActive,
            onConnect = onConnectGoogle,
            onDisconnect = onDisconnectGoogle,
        )
        WebDavCard(
            isActive = isWebDavActive,
            isOtherProviderActive = isGoogleActive,
            isConnecting = isWebDavConnecting,
            host = account?.takeIf { isWebDavActive }?.accountLabel,
            onConnect = onConnectWebDav,
            onDisconnect = onDisconnectWebDav,
        )
        LocalBackupCard(onOpenLocalBackup)
    }
}

@Composable
private fun GoogleDriveCard(
    syncUiState: SyncUiState,
    isAuthenticating: Boolean,
    isConfigured: Boolean,
    isOtherProviderActive: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val account = when (syncUiState) {
        is SyncUiState.Configured -> syncUiState.account
        is SyncUiState.Syncing -> syncUiState.account
        else -> null
    }
    val isActive = account != null
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    Card(
        colors = when {
            isOtherProviderActive -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            )
            isActive -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            )
            else -> CardDefaults.cardColors()
        },
        border = if (isActive && !isOtherProviderActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
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
                OutlinedButton(onClick = { showDisconnectConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Déconnecter")
                }
            } else {
                Spacer(Modifier.height(12.dp))
                if (isOtherProviderActive) {
                    // Lot 19 — exclusivité mutuelle WebDAV/Google Drive
                    // (décision actée) : la carte est grisée, pas un
                    // contrôle décoratif — le message explique pourquoi.
                    Text(
                        "Désactivez WebDAV pour connecter Google Drive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (!isConfigured && !isOtherProviderActive) {
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
                Button(
                    onClick = onConnect,
                    enabled = !isAuthenticating && isConfigured && !isOtherProviderActive,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("Se connecter avec Google")
                }
            }
        }
    }

    if (showDisconnectConfirm) {
        // Retour Issa (vérification device) : la déconnexion agissait
        // sans confirmation, un seul appui malheureux suffisait à
        // rompre le lien. Même patron que ConfirmDialog (feature/settings).
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Déconnecter Google Drive ?") },
            text = { Text("La synchronisation s'arrêtera. Vous pourrez vous reconnecter à tout moment.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectConfirm = false
                        onDisconnect()
                    },
                ) { Text("Déconnecter", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDisconnectConfirm = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun WebDavCard(
    isActive: Boolean,
    isOtherProviderActive: Boolean,
    isConnecting: Boolean,
    host: String?,
    onConnect: (url: String, username: String, password: String) -> Unit,
    onDisconnect: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    Card(
        colors = when {
            isOtherProviderActive -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            )
            isActive -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            )
            else -> CardDefaults.cardColors()
        },
        border = if (isActive && !isOtherProviderActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("WebDAV", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                StatusBadge(isActive)
            }

            if (isActive) {
                // Le mot de passe persisté n'est jamais ré-affiché (secret,
                // même discipline que le jeton OAuth) : l'état actif montre
                // l'hôte lié + « Connecté », pas les champs d'édition.
                Spacer(Modifier.height(8.dp))
                Text("Connecté", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                host?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { showDisconnectConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Déconnecter")
                }
            } else {
                Spacer(Modifier.height(12.dp))
                if (isOtherProviderActive) {
                    Text(
                        "Désactivez Google Drive pour connecter WebDAV.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://exemple.com/webdav/") },
                    singleLine = true,
                    enabled = !isOtherProviderActive,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Identifiant") },
                    singleLine = true,
                    enabled = !isOtherProviderActive,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isOtherProviderActive,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onConnect(url.trim(), username, password) },
                    enabled = !isConnecting && !isOtherProviderActive && url.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("Tester et connecter")
                }
            }
        }
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Déconnecter WebDAV ?") },
            text = { Text("La synchronisation s'arrêtera. Vous pourrez vous reconnecter à tout moment.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectConfirm = false
                        onDisconnect()
                    },
                ) { Text("Déconnecter", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDisconnectConfirm = false }) { Text("Annuler") } },
        )
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

/**
 * Écran Opérationnel / Dashboard (tâche 11.8) : header profil, action
 * rapide, flotte d'appareils, deux interrupteurs, journal d'activité.
 * Bannière persistante en tête si la dernière synchro automatique a
 * échoué (`syncLastAutoSyncFailed`) — une snackbar reste le retour des
 * actions manuelles (déjà utilisée pour Export/Import), pas d'une
 * synchro automatique en échec pendant que l'utilisateur est ailleurs.
 */
@Composable
private fun SyncOperationalContent(state: SyncScreenState, onIntent: (SyncIntent) -> Unit) {
    val account = when (val syncUiState = state.syncUiState) {
        is SyncUiState.Configured -> syncUiState.account
        is SyncUiState.Syncing -> syncUiState.account
        else -> null
    } ?: return
    val isSyncing = state.syncUiState is SyncUiState.Syncing

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (account.lastAutoSyncFailed) {
            item { AutoSyncFailedBanner() }
        }
        item { ProfileHeader(account.accountLabel) }
        item {
            QuickSyncAction(
                lastSyncAt = account.lastSyncAt,
                isSyncing = isSyncing,
                onSynchronizeNow = { onIntent(SyncIntent.SynchronizeNow) },
            )
        }
        item {
            DeviceFleetCard(
                fleet = state.fleet,
                currentDeviceId = state.currentDeviceId,
                onRemoveDevice = { onIntent(SyncIntent.RemoveDevice(it)) },
            )
        }
        item {
            SyncTogglesCard(
                autoEnabled = state.syncAutoEnabled,
                wifiOnly = state.syncWifiOnly,
                onSetAutoEnabled = { onIntent(SyncIntent.SetAutoSyncEnabled(it)) },
                onSetWifiOnly = { onIntent(SyncIntent.SetWifiOnly(it)) },
            )
        }
        item { ActivityLogCard(state.activityLog) }
    }
}

@Composable
private fun AutoSyncFailedBanner() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.ErrorOutlined, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                "La dernière synchronisation automatique a échoué. Réessayez manuellement ci-dessous.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ProfileHeader(accountLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                accountLabel.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(accountLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(
                "Google Drive actif",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickSyncAction(lastSyncAt: Long?, isSyncing: Boolean, onSynchronizeNow: () -> Unit) {
    Column {
        Text(
            lastSyncAt?.let { "Dernière synchro : ${relativeTime(it)}" } ?: "Aucune synchronisation encore effectuée",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // Tâche 11.9, point 6 — le bouton se désactive dès que l'état
        // reflète une synchro en cours (SyncUiState.Syncing), qu'elle ait
        // été déclenchée ici ou par le Worker automatique.
        Button(onClick = onSynchronizeNow, enabled = !isSyncing, modifier = Modifier.fillMaxWidth()) {
            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Synchronisation en cours...")
            } else {
                Text("Synchroniser maintenant")
            }
        }
    }
}

@Composable
private fun DeviceFleetCard(fleet: List<DeviceFleetEntry>, currentDeviceId: String?, onRemoveDevice: (String) -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Appareils (${fleet.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            if (fleet.isEmpty()) {
                Text(
                    "Aucun appareil synchronisé pour l'instant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            fleet.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                DeviceRow(entry, isCurrent = entry.deviceId == currentDeviceId, onRemove = { onRemoveDevice(entry.deviceId) })
            }
        }
    }
}

@Composable
private fun DeviceRow(entry: DeviceFleetEntry, isCurrent: Boolean, onRemove: () -> Unit) {
    var showRemoveConfirm by remember { mutableStateOf(false) }
    // Fraîcheur arbitraire à 24h — un point vert signifie "vu récemment",
    // jamais "en ligne maintenant" (aucun signal de présence, tâche 11.8).
    val isFresh = System.currentTimeMillis() - entry.lastActiveAt < DateUtils.DAY_IN_MILLIS

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Device, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.displayName, style = MaterialTheme.typography.bodyMedium)
                if (isCurrent) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "(Cet appareil)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                "Vu ${relativeTime(entry.lastActiveAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier.size(8.dp).clip(CircleShape)
                .background(if (isFresh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { showRemoveConfirm = true }) {
            Icon(AppIcons.Delete, contentDescription = "Retirer ${entry.displayName} de la liste")
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Retirer de la liste ?") },
            // Libellé volontairement pas "Révoquer" (tâche 11.8) : sans
            // serveur, ce n'est qu'un nettoyage de liste, aucune sécurité
            // n'est retirée — l'appareil réapparaît s'il se synchronise à nouveau.
            text = { Text("${entry.displayName} disparaîtra de cette liste. Il réapparaîtra automatiquement s'il se synchronise à nouveau.") },
            confirmButton = {
                TextButton(onClick = { showRemoveConfirm = false; onRemove() }) {
                    Text("Retirer de la liste", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showRemoveConfirm = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun SyncTogglesCard(autoEnabled: Boolean, wifiOnly: Boolean, onSetAutoEnabled: (Boolean) -> Unit, onSetWifiOnly: (Boolean) -> Unit) {
    Card {
        Column(Modifier.padding(vertical = 8.dp)) {
            ToggleRow(
                label = "Synchro automatique en arrière-plan",
                checked = autoEnabled,
                enabled = true,
                onCheckedChange = onSetAutoEnabled,
            )
            // Grisé quand l'auto-sync est éteinte — même patron que
            // l'intervalle de repos oculaire (feature/settings, lot 6) :
            // rien à restreindre s'il n'y a pas de synchro automatique.
            ToggleRow(
                label = "Wi-Fi uniquement",
                checked = wifiOnly,
                enabled = autoEnabled,
                onCheckedChange = onSetWifiOnly,
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = contentColor, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun ActivityLogCard(events: List<SyncActivityEvent>) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Journal d'activité", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (events.isEmpty()) {
                Text(
                    "Aucun événement pour l'instant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            events.forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider()
                ActivityRow(event)
            }
        }
    }
}

@Composable
private fun ActivityRow(event: SyncActivityEvent) {
    // Icône de forme distincte par type (tâche 11.8/11.9, point 9) — la
    // couleur vient en renfort, jamais seule (daltonisme/TalkBack).
    val (icon, tint) = when (event.type) {
        SyncActivityEventType.SUCCESS -> AppIcons.Success to MaterialTheme.colorScheme.primary
        SyncActivityEventType.NETWORK_FAILURE -> AppIcons.ErrorOutlined to MaterialTheme.colorScheme.error
        SyncActivityEventType.MANUAL_SYNC -> AppIcons.Refresh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(event.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(relativeTime(event.occurredAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun relativeTime(timestamp: Long): String =
    DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
