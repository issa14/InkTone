package com.inktone.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.inktone.core.designsystem.AppIcon
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
 * réinitialiserait le filtre serveur SERIES/TAG qui définit cet écran.
 * [showLayoutSection] = false au même endroit : cet écran est toujours
 * en vue Liste (décision de la cible), pas de bascule à y proposer.
 */
private val StatusFilterOptions = listOf(FilterMode.ALL, FilterMode.UNREAD, FilterMode.IN_PROGRESS, FilterMode.READ)

private fun FilterMode.statusLabel() = when (this) {
    FilterMode.ALL -> "Tous"
    FilterMode.UNREAD -> "Non lu"
    FilterMode.IN_PROGRESS -> "En cours"
    FilterMode.READ -> "Terminé"
    else -> name
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryFilterDialog(
    sortOrder: LibrarySortOrder,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
    statusFilter: FilterMode = FilterMode.ALL,
    onStatusFilterChange: (FilterMode) -> Unit = {},
    layoutMode: LibraryLayoutMode = LibraryLayoutMode.LIST,
    onLayoutModeChange: (LibraryLayoutMode) -> Unit = {},
    selectedFormats: Set<PublicationFormat>,
    onToggleFormat: (PublicationFormat) -> Unit,
    onClearFormats: () -> Unit,
    onDismiss: () -> Unit,
    showStatusFilter: Boolean = true,
    showLayoutSection: Boolean = true,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // Les deux colonnes ci-dessous ne tiennent pas dans la largeur par
        // defaut d'un Dialog des que la police grossit (prereglage
        // d'accessibilite : corps 24 + OpenDyslexic). On prend la largeur
        // disponible et on la borne, plutot que de laisser « Date d'import »
        // se faire couper.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 480.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                // ── Ligne 1 : deux colonnes (memes 4 options chacune) ──
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
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
                    }
                    if (showStatusFilter) {
                        Column(Modifier.weight(1f)) {
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
                    }
                }

                // ── Ligne 2 : type de fichier, en puces horizontales ──
                FilterSectionTitle("Type de fichier")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // « Tous » n'est pas un format : c'est l'absence de
                    // filtre, donc l'ensemble vide — d'ou un clic qui efface
                    // au lieu de basculer.
                    FilterChip(
                        selected = selectedFormats.isEmpty(),
                        onClick = onClearFormats,
                        label = { Text("Tous") },
                    )
                    // PDF etait absent de ce dialogue alors que
                    // PublicationFormat le porte depuis le Lot 12 : les PDF
                    // importes n'etaient tout simplement pas filtrables.
                    PublicationFormat.entries.forEach { format ->
                        FilterChip(
                            selected = format in selectedFormats,
                            onClick = { onToggleFormat(format) },
                            label = { Text(format.name) },
                        )
                    }
                }

                // ── Ligne 3 : mise en page, icone + libelle ──
                if (showLayoutSection) {
                    FilterSectionTitle("Mise en page")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LibraryLayoutMode.entries.forEach { mode ->
                            LayoutModeCell(
                                mode = mode,
                                selected = layoutMode == mode,
                                onClick = { onLayoutModeChange(mode) },
                                // Tiers egaux : les libelles ont des
                                // longueurs tres inegales (« Liste » contre
                                // « Couvertures seules »), les laisser
                                // dimensionner les cellules donnerait trois
                                // largeurs differentes.
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Cellule de choix de disposition — icone au-dessus, libelle dessous.
 *
 * Le libelle est indispensable depuis qu'il existe trois modes : les
 * icones de « Couvertures seules » et « Grille detaillee » ne se
 * distinguent pas d'un coup d'oeil. Il est vertical et sur deux lignes
 * parce que « Couvertures seules » ne tient pas sur un tiers de la
 * largeur du dialogue en une seule ligne.
 */
@Composable
private fun LayoutModeCell(
    mode: LibraryLayoutMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(12.dp),
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppIcon(
            mode.icon(),
            contentDescription = null, // porte par le libelle ci-dessous
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = mode.label(),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
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
