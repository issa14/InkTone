package com.inktone.feature.library

import com.inktone.core.designsystem.InkToneSpacing

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.inktone.core.designsystem.AppIcon
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
    showDetailsButton: Boolean = true,
) {
    val successCount = results.count { it.resultType == "success" }
    val duplicateCount = results.count { it.resultType == "duplicate" }
    val corruptedCount = results.count { it.resultType == "corrupted" }
    val drmCount = results.count { it.resultType == "drm_protected" }
    val unsupportedCount = results.count { it.resultType == "unsupported_format" }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = InkToneSpacing.sm, vertical = InkToneSpacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(InkToneSpacing.md),
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
                )
            }

            Spacer(Modifier.width(InkToneSpacing.sm))

            if (showDetailsButton) {
                TextButton(onClick = onDetailsClick) {
                    Text("Détails")
                }
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
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = InkToneSpacing.sm, vertical = InkToneSpacing.xs)) {
        // Résumé en-tête
        // En-tête de la vue détaillée : le bouton « Détails » y restait
        // affiché et ne faisait rien.
        ImportResultSummary(
            results = results,
            onDetailsClick = {},
            onDismiss = onDismiss,
            showDetailsButton = false,
        )

        Spacer(Modifier.height(InkToneSpacing.xs))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            LazyColumn(modifier = Modifier.padding(InkToneSpacing.sm)) {
                // Pas de clé dérivée du contenu : deux fichiers de même nom et
                // de même issue dans un lot (deux « livre.epub » de dossiers
                // différents, tous deux en doublon) produisaient la même clé,
                // et LazyColumn lève une exception sur une clé dupliquée. La
                // liste est figée une fois affichée, la position suffit.
                items(results) { entry ->
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
        AppIcon(
            when {
                isAlert -> AppSymbol.Error
                isDuplicate -> AppSymbol.Reading
                else -> AppSymbol.Success
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

        Spacer(Modifier.width(InkToneSpacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val reason = importFailureReason(entry.resultType)
            if (reason != null) {
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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

/**
 * Motif d'échec d'un fichier importé, lisible par tous : le message stocké
 * vient du parseur (chemin, exception) et ne s'affiche pas (audit UI P1-13).
 */
internal fun importFailureReason(resultType: String): String? = when (resultType) {
    "drm_protected" -> "Protégé par DRM : ne peut pas être ouvert."
    "corrupted" -> "Fichier endommagé ou illisible."
    "unsupported_format" -> "Format non pris en charge."
    else -> null
}
