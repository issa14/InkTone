package com.inktone.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inktone.core.designsystem.AppIcons
import com.inktone.core.designsystem.AppSymbol
import com.inktone.domain.service.ImportResultEntry

/**
 * Résumé de fin de lot d'import (Palier B, Lot 5).
 *
 * Affiche un résumé du type « 9 importés · 2 doublons ignorés ·
 * 1 fichier corrompu » avec un bouton « Détails ». Les catégories
 * vides ne sont pas affichées. Non bloquant — rendu au-dessus du
 * contenu, jamais en overlay.
 */
@Composable
fun ImportResultSummary(
    results: List<ImportResultEntry>,
    onDetailsClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val successCount = results.count { it.resultType == "success" }
    val duplicateCount = results.count { it.resultType == "duplicate" }
    val corruptedCount = results.count { it.resultType == "corrupted" }
    val drmCount = results.count { it.resultType == "drm_protected" }
    val unsupportedCount = results.count { it.resultType == "unsupported_format" }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val parts = mutableListOf<String>()
                if (successCount > 0) parts += "$successCount importé${if (successCount > 1) "s" else ""}"
                if (duplicateCount > 0) parts += "$duplicateCount doublon${if (duplicateCount > 1) "s" else ""} ignoré${if (duplicateCount > 1) "s" else ""}"
                if (corruptedCount > 0) parts += "$corruptedCount fichier${if (corruptedCount > 1) "s" else ""} corrompu${if (corruptedCount > 1) "s" else ""}"
                if (drmCount > 0) parts += "$drmCount fichier${if (drmCount > 1) "s" else ""} protégé${if (drmCount > 1) "s" else ""} par DRM"
                if (unsupportedCount > 0) parts += "$unsupportedCount format${if (unsupportedCount > 1) "s" else ""} non pris en charge"

                Text(
                    text = parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.width(8.dp))

            TextButton(onClick = onDetailsClick) {
                Text("Détails")
            }

            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    }
}

/**
 * Écran de détail des résultats d'import (Palier B, Lot 5).
 *
 * Deux registres visuels distincts :
 * - **Informationnel** : `Duplicate` (le livre est déjà présent)
 * - **Alerte** : `Corrupted`, `DrmProtected`, `UnsupportedFormat`
 *
 * Pour un `Duplicate`, un bouton permet d'ouvrir le livre déjà présent
 * (`existingPublicationId`). Aucun bouton « Réessayer » sur les erreurs
 * non réessayables (DRM, format non supporté).
 */
@Composable
fun ImportResultDetail(
    results: List<ImportResultEntry>,
    onOpenPublication: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        // Résumé en-tête
        ImportResultSummary(
            results = results,
            onDetailsClick = {}, // déjà dans les détails
            onDismiss = onDismiss,
        )

        Spacer(Modifier.height(4.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                items(results, key = { "${it.resultType}_${it.fileName}" }) { entry ->
                    ImportResultRow(entry = entry, onOpenPublication = onOpenPublication)
                }
            }
        }
    }
}

@Composable
private fun ImportResultRow(
    entry: ImportResultEntry,
    onOpenPublication: (String) -> Unit,
) {
    val isAlert = entry.resultType in setOf("corrupted", "drm_protected", "unsupported_format")
    val isDuplicate = entry.resultType == "duplicate"
    val isSuccess = entry.resultType == "success"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when {
                isAlert -> AppIcons.Error
                isDuplicate -> AppIcons.Reading
                else -> AppIcons.Success
            },
            contentDescription = when {
                isAlert -> "Alerte"
                isDuplicate -> "Doublon"
                else -> "Réussi"
            },
            modifier = Modifier.size(20.dp),
            tint = when {
                isAlert -> MaterialTheme.colorScheme.error
                isDuplicate -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.primary
            },
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            if (entry.message != null) {
                Text(
                    text = entry.message!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }

        if (isDuplicate && entry.existingPublicationId != null) {
            Button(
                onClick = { onOpenPublication(entry.existingPublicationId!!) },
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Text("Ouvrir", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
