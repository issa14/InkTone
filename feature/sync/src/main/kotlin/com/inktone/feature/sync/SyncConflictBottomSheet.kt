package com.inktone.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.text.format.DateUtils
import com.inktone.core.designsystem.AppIcons
import com.inktone.domain.model.ReadingPositionSnapshot
import java.util.Locale

/**
 * Conçu directement en code, sans maquette préalable (tâche 11.10,
 * exception assumée à la méthode habituelle) — `UX_FLOW_DESIGN.md` ne
 * décrivait aucun écran de conflit, c'est un manque de conception, pas
 * un oubli d'implémentation.
 *
 * N'arbitre **que** la position de lecture — aucun chemin d'ici ne
 * permet d'écraser en bloc les annotations ou les marque-pages (fusion
 * silencieuse, jamais de question posée pour ces catégories).
 * [PendingConflictsViewModel] présente les conflits en file : cet appel
 * ne doit afficher qu'un conflit à la fois, l'appelant relance
 * l'affichage tant que la liste n'est pas vide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncConflictBottomSheet(
    viewModel: PendingConflictsViewModel = hiltViewModel(),
) {
    val conflicts by viewModel.conflicts.collectAsState()
    val conflict = conflicts.firstOrNull() ?: return
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = { /* pas de rejet silencieux : un conflit doit être tranché, pas escamoté */ }, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.WarningOutlined, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Row(Modifier.padding(start = 8.dp)) {
                    Text("Conflit de synchronisation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Text(conflict.bookTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                "Ce livre a été lu sur deux appareils à des endroits différents. Choisissez où reprendre.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PositionOption(
                originLabel = "Sur cet appareil",
                snapshot = conflict.local,
                actionLabel = "Reprendre au chapitre ${conflict.local.chapterIndex + 1}",
                onChoose = { viewModel.resolve(conflict, conflict.local.locator) },
            )
            PositionOption(
                originLabel = "Depuis ${conflict.remote.deviceLabel}",
                snapshot = conflict.remote,
                actionLabel = "Reprendre au chapitre ${conflict.remote.chapterIndex + 1}",
                onChoose = { viewModel.resolve(conflict, conflict.remote.locator) },
            )
        }
    }
}

@Composable
private fun PositionOption(originLabel: String, snapshot: ReadingPositionSnapshot, actionLabel: String, onChoose: () -> Unit) {
    // La version la plus récente est signalée, jamais présélectionnée
    // (tâche 11.10) : la fraîcheur n'est qu'une information de contexte
    // parmi d'autres, pas un choix par défaut.
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(originLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                DateUtils.getRelativeTimeSpanString(snapshot.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Chapitre ${snapshot.chapterIndex + 1}, ${formatPercent(snapshot.progressFraction)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onChoose, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(actionLabel)
            }
        }
    }
}

private fun formatPercent(fraction: Float): String = String.format(Locale.FRANCE, "%.1f %%", fraction * 100)
