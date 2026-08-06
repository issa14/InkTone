package com.inktone.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inktone.core.designsystem.AppIcons
import com.inktone.domain.model.Publication
import java.text.DateFormat
import java.util.Date
import kotlin.math.log10
import kotlin.math.pow

/**
 * Popup d'actions par livre (UX §Bibliothèque état peuplé) — remplace
 * les points décoratifs (lot 2b.3). 3 actions, pas 4 : « Télécharger la
 * couverture » retirée de la cible (décision actée, voir
 * UX_FLOW_DESIGN.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookActionsSheet(
    publication: Publication,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onShowDetails: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 32.dp)) {
            Text(
                publication.title,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            BookActionItem(
                label = if (publication.isPinned) "Détacher" else "Épingler",
                icon = if (publication.isPinned) AppIcons.Pin else AppIcons.PinOutlined,
                onClick = { onDismiss(); onTogglePin() },
            )
            BookActionItem(
                label = "Détails du livre",
                icon = AppIcons.Info,
                onClick = { onDismiss(); onShowDetails() },
            )
            BookActionItem(
                label = "Retirer de la bibliothèque",
                icon = AppIcons.Delete,
                onClick = { onDismiss(); onRequestDelete() },
            )
        }
    }
}

@Composable
private fun BookActionItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Confirmation de suppression — texte obligatoire (UX §Bibliothèque état
 * peuplé et §Marque-pages vue globale, cité deux fois dans la cible) :
 * action irréversible, précise explicitement que les marque-pages et
 * notes associés au livre seront également supprimés. Ne pas abréger.
 */
@Composable
fun DeleteConfirmationDialog(
    publicationTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Retirer « $publicationTitle » ?") },
        text = {
            Text(
                "Cette action est irréversible. Les marque-pages et notes associés à ce livre seront également supprimés.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Retirer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

/**
 * Détails du livre — bottom sheet alimenté par les champs déjà présents
 * dans [Publication], aucune donnée à créer (UX §Bibliothèque état
 * peuplé).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsSheet(publication: Publication, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(publication.title, style = MaterialTheme.typography.titleLarge)
            if (!publication.subtitle.isNullOrBlank()) {
                Text(publication.subtitle!!, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (publication.authors.isNotEmpty()) DetailRow("Auteurs", publication.authors.joinToString())
            publication.publisher?.let { DetailRow("Éditeur", it) }
            publication.language?.let { DetailRow("Langue", it) }
            DetailRow("Format", publication.format.name)
            DetailRow("Taille", formatFileSize(publication.fileSize))
            DetailRow("Chapitres", publication.chapterCount.toString())
            if (publication.subjects.isNotEmpty()) DetailRow("Sujets", publication.subjects.joinToString())
            DetailRow("Importé le", DateFormat.getDateInstance().format(Date(publication.importDate)))
            if (!publication.description.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    publication.description!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 o"
    val units = arrayOf("o", "Ko", "Mo", "Go")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / 1024.0.pow(digitGroups)
    return "%.1f %s".format(value, units[digitGroups])
}
