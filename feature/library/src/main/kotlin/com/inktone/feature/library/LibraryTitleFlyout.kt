package com.inktone.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Flyout du titre (UX §Menu déroulant du titre) — deux colonnes, remplace
 * le `ModalBottomSheet` à une colonne du lot 1 (lot 2a, tâche 2a.3).
 *
 * Colonne gauche : catégories fixes. Colonne droite : sous-éléments,
 * uniquement pour Séries et Tags — sélectionner la catégorie elle-même
 * révèle la colonne droite (état local, ne ferme pas le flyout) ; taper
 * un sous-élément navigue vers l'écran de détail (2a.4) et ferme.
 */
private enum class FlyoutCategory { SERIES, TAGS }

@Composable
fun LibraryTitleFlyout(
    expanded: Boolean,
    onDismiss: () -> Unit,
    series: List<String>,
    seriesCounts: Map<String, Int>,
    tags: List<String>,
    tagCounts: Map<String, Int>,
    onSelectAll: () -> Unit,
    onSelectFavorites: () -> Unit,
    onNavigateToSeriesDetail: (String) -> Unit,
    onNavigateToTagDetail: (String) -> Unit,
) {
    var activeCategory by remember { mutableStateOf<FlyoutCategory?>(null) }
    LaunchedEffect(expanded) {
        if (!expanded) activeCategory = null
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Row {
            Column(Modifier.width(140.dp).padding(vertical = 4.dp)) {
                FlyoutCategoryRow("Tous") { onSelectAll(); onDismiss() }
                FlyoutCategoryRow("Favoris") { onSelectFavorites(); onDismiss() }
                FlyoutCategoryRow(
                    label = "Séries",
                    selected = activeCategory == FlyoutCategory.SERIES,
                ) { activeCategory = FlyoutCategory.SERIES }
                FlyoutCategoryRow(
                    label = "Tags",
                    selected = activeCategory == FlyoutCategory.TAGS,
                ) { activeCategory = FlyoutCategory.TAGS }
            }

            if (activeCategory != null) {
                HorizontalDivider(
                    modifier = Modifier.width(1.dp).padding(vertical = 4.dp),
                )
                val entries = when (activeCategory) {
                    FlyoutCategory.SERIES -> series.map { it to (seriesCounts[it] ?: 0) }
                    FlyoutCategory.TAGS -> tags.map { it to (tagCounts[it] ?: 0) }
                    null -> emptyList()
                }
                val onNavigate = when (activeCategory) {
                    FlyoutCategory.SERIES -> onNavigateToSeriesDetail
                    FlyoutCategory.TAGS -> onNavigateToTagDetail
                    null -> { _: String -> }
                }
                LazyColumn(Modifier.width(180.dp).padding(vertical = 4.dp)) {
                    items(entries, key = { it.first }) { (name, count) ->
                        FlyoutCategoryRow("$name ($count)") { onNavigate(name); onDismiss() }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlyoutCategoryRow(label: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
