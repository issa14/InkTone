package com.inktone.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.inktone.core.designsystem.AppIcons
import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.PublicationFormat

/**
 * Popup de filtrage (UX §Popup de filtrage, icône a3) — dialogue centré,
 * volontairement distinct visuellement du bottom sheet du menu 3-points.
 * Remplace le menu de tri et la bascule de disposition de la barre du
 * haut (lot 2a, tâche 2a.2).
 *
 * [showStatusFilter] = false depuis l'écran de détail Séries/Tags
 * (lot 2a.4) : y appliquer un changement de statut (ALL/UNREAD/...)
 * réinitialiserait le filtre serveur SERIES/TAG qui définit cet écran —
 * seuls tri, mise en page et type de fichier y ont un sens.
 */
private val StatusFilterOptions = listOf(FilterMode.ALL, FilterMode.UNREAD, FilterMode.IN_PROGRESS, FilterMode.READ)

private fun FilterMode.statusLabel() = when (this) {
    FilterMode.ALL -> "Tous"
    FilterMode.UNREAD -> "Non lu"
    FilterMode.IN_PROGRESS -> "En cours"
    FilterMode.READ -> "Terminé"
    else -> name
}

@Composable
fun LibraryFilterDialog(
    sortOrder: LibrarySortOrder,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
    statusFilter: FilterMode,
    onStatusFilterChange: (FilterMode) -> Unit,
    layoutMode: LibraryLayoutMode,
    onLayoutModeChange: (LibraryLayoutMode) -> Unit,
    selectedFormats: Set<PublicationFormat>,
    onToggleFormat: (PublicationFormat) -> Unit,
    onClearFormats: () -> Unit,
    onDismiss: () -> Unit,
    showStatusFilter: Boolean = true,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                FilterSectionTitle("Trier par")
                Column(Modifier.selectableGroup()) {
                    LibrarySortOrder.entries.forEach { order ->
                        FilterRadioRow(
                            label = order.label(),
                            selected = sortOrder == order,
                            onClick = { onSortOrderChange(order) },
                        )
                    }
                }

                if (showStatusFilter) {
                    FilterSectionTitle("Filtrer par")
                    Column(Modifier.selectableGroup()) {
                        StatusFilterOptions.forEach { mode ->
                            FilterRadioRow(
                                label = mode.statusLabel(),
                                selected = statusFilter == mode,
                                onClick = { onStatusFilterChange(mode) },
                            )
                        }
                    }
                }

                FilterSectionTitle("Mise en page")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    LibraryLayoutMode.entries.forEach { mode ->
                        val selected = layoutMode == mode
                        IconButton(
                            onClick = { onLayoutModeChange(mode) },
                            modifier = Modifier
                                .background(
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                ),
                        ) {
                            Icon(
                                mode.icon(),
                                contentDescription = mode.label(),
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                FilterSectionTitle("Type de fichier")
                FilterCheckboxRow(
                    label = "Tous",
                    checked = selectedFormats.isEmpty(),
                    onClick = onClearFormats,
                )
                FilterCheckboxRow(
                    label = "EPUB",
                    checked = PublicationFormat.EPUB in selectedFormats,
                    onClick = { onToggleFormat(PublicationFormat.EPUB) },
                )
                FilterCheckboxRow(
                    label = "TXT",
                    checked = PublicationFormat.TXT in selectedFormats,
                    onClick = { onToggleFormat(PublicationFormat.TXT) },
                )
            }
        }
    }
}

@Composable
private fun FilterSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FilterRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun FilterCheckboxRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onClick() })
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
