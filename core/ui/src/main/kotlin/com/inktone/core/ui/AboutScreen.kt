package com.inktone.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tache 9bis.6 — port direct de l'ecran legacy (structure identique :
 * en-tete, description, section confidentialite, section technique,
 * credits). Mentions de bibliotheques mises a jour pour refleter les
 * dependances REELLEMENT integrees a cette date, pas celles du legacy —
 * `Kokoro` (docs/execution/PROTOTYPE_SYNTHESE_KOKORO_ONNX.md) n'est qu'un
 * prototype documente, jamais integre au code de production, donc pas
 * cite ici (Blueprint §17.2, le code fait foi).
 */
@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(64.dp).width(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("InkTone", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        Text("Version 0.1.0", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

        Spacer(Modifier.height(20.dp))

        InfoCard(
            "InkTone est un lecteur EPUB avec narration TTS neuronale synchronisée " +
                "mot à mot, entièrement local, conçu pour transformer vos livres numériques " +
                "en expérience d'écoute fluide et accessible, directement sur votre appareil.",
        )

        Spacer(Modifier.height(12.dp))
        SectionHeader("Confidentialité")
        InfoCard("Synthèse vocale et traitement du texte s'exécutent intégralement sur l'appareil (Sherpa-ONNX/onnxruntime) — aucun texte n'est envoyé à un serveur tiers.")
        InfoCard("Fonctionne hors ligne. Le rapport de plantage est opt-in et désactivé par défaut (voir Réglages > Confidentialité).")

        Spacer(Modifier.height(12.dp))
        SectionHeader("Bibliothèques et licences")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                LicenseRow("Readium (parseur EPUB)", "BSD-3-Clause")
                LicenseRow("Sherpa-ONNX / ONNX Runtime (synthèse vocale)", "Apache-2.0 / MIT")
                LicenseRow("Jetpack Compose, Room, Hilt, Media3, WorkManager", "Apache-2.0")
                LicenseRow("Police OpenDyslexic", "SIL Open Font License 1.1")
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("Crédits")
        InfoCard("Code source : github.com/issa14/InkTone")

        Spacer(Modifier.height(16.dp))
        Text("© 2026 InkTone.", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 11.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun LicenseRow(label: String, license: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(license, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}
