package com.inktone.core.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import kotlinx.coroutines.launch

private const val SUPPORT_EMAIL = "issadotnet@gmail.com"

/**
 * D.5 — Version dynamique, liens cliquables, mentions techniques corrigées.
 * Lot 19 — refonte conforme à `UX_FLOW_DESIGN.md` §« Écran : À propos » :
 * badge de version cliquable (court/long), grille 3 colonnes Engagements &
 * Confidentialité, cartes GitHub et « Signaler un problème », accordéon
 * « Architecture Tech & Licences » avec badges colorés.
 *
 * [versionName] permet au caller (module `app`) de fournir
 * `BuildConfig.VERSION_NAME` — `core:ui` n'a pas `buildConfig` activé.
 */
@Composable
fun AboutScreen(versionName: String = "0.1.0") {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            AppIcon(AppSymbol.Reading, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(64.dp).width(64.dp))
            Spacer(Modifier.height(12.dp))
            Text("InkTone", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Spacer(Modifier.height(6.dp))
            VersionBadge(
                versionName = versionName,
                onClick = {
                    scope.launch { snackbarHostState.showSnackbar("Version officielle") }
                },
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(buildDiagnostics(versionName)))
                    scope.launch { snackbarHostState.showSnackbar("Diagnostic copié dans le presse-papier") }
                },
            )

            Spacer(Modifier.height(20.dp))

            InfoCard(
                "InkTone est un lecteur EPUB avec narration TTS neuronale synchronisée " +
                    "mot à mot, entièrement local, conçu pour transformer vos livres numériques " +
                    "en expérience d'écoute fluide et accessible, directement sur votre appareil.",
            )

            Spacer(Modifier.height(16.dp))
            EngagementsGrid()

            Spacer(Modifier.height(12.dp))
            SectionHeader("Ressources & Support")
            GitHubCard()
            Spacer(Modifier.height(8.dp))
            ReportProblemCard(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                        putExtra(Intent.EXTRA_SUBJECT, "Problème InkTone")
                        putExtra(Intent.EXTRA_TEXT, buildDiagnostics(versionName))
                    }
                    runCatching { context.startActivity(Intent.createChooser(intent, "Signaler un problème")) }
                },
            )

            Spacer(Modifier.height(12.dp))
            ArchitectureAccordion()

            Spacer(Modifier.height(16.dp))
            Text("© ${java.time.Year.now().value} InkTone.", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 11.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Diagnostics système copiés au presse-papier (clic long sur le badge de version). */
private fun buildDiagnostics(versionName: String): String = buildString {
    appendLine("Version : $versionName")
    appendLine("Modèle : ${android.os.Build.MODEL}")
    appendLine("OS : Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
    appendLine("Moteur TTS : Sherpa-ONNX (Kokoro)")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VersionBadge(
    versionName: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            "v$versionName",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 13.sp,
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun EngagementsGrid() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EngagementPillar(
            icon = AppSymbol.Device,
            title = "100% Local",
            subtitle = "Inférence CPU",
            modifier = Modifier.weight(1f),
        )
        EngagementPillar(
            icon = AppSymbol.Lock,
            title = "Vie Privée",
            subtitle = "Stockage isolé",
            modifier = Modifier.weight(1f),
        )
        EngagementPillar(
            icon = AppSymbol.CloudDisconnected,
            title = "Hors-Ligne",
            subtitle = "Zéro serveur",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EngagementPillar(icon: AppSymbol, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, textAlign = TextAlign.Center)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun GitHubCard() {
    val uriHandler = LocalUriHandler.current
    Card(
        onClick = { uriHandler.openUri("https://github.com/issa14/InkTone") },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(AppSymbol.Article, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Dépôt GitHub officiel", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("github.com/issa14/InkTone", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ReportProblemCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(AppSymbol.WarningAmber, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Signaler un problème", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Email pré-rempli avec le diagnostic système", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ArchitectureAccordion() {
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            SectionHeader(
                text = "Architecture Tech & Licences",
                trailing = {
                    AppIcon(
                        if (expanded) AppSymbol.KeyboardArrowUp else AppSymbol.KeyboardArrowDown,
                        contentDescription = if (expanded) "Replier" else "Déplier",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = { expanded = !expanded },
            )
            if (expanded) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
                    LicenseRow("Readium (parseur EPUB)", "BSD-3-Clause", Color(0xFF00695C))
                    LicenseRow("Kokoro (synthèse vocale)", "Apache-2.0", Color(0xFF2E7D32))
                    LicenseRow("ONNX Runtime (inférence)", "MIT", Color(0xFF1565C0))
                    LicenseRow("Jetpack Compose, Room, Hilt, Media3, WorkManager", "Apache-2.0", Color(0xFF2E7D32))
                    LicenseRow("Police OpenDyslexic", "SIL Open Font License 1.1", Color(0xFFE65100))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun SectionHeader(
    text: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Justify, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun LicenseRow(label: String, license: String, badgeColor: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(badgeColor)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(license, color = Color.White, fontSize = 11.sp)
        }
    }
}
