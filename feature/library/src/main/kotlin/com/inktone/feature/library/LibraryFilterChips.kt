package com.inktone.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import com.inktone.core.designsystem.InkToneSpacing
import com.inktone.domain.model.FilterMode

/**
 * Filtres directs, par fréquence d'usage : un geste, et le filtre actif reste
 * visible (audit UX P1, navigation). Séries et Étiquettes, qui ouvrent une
 * liste au lieu de filtrer, viennent après.
 */
private val DirectFilters = listOf(
    FilterMode.ALL to "Tous",
    FilterMode.IN_PROGRESS to "En cours",
    FilterMode.FAVORITES to "Favoris",
    FilterMode.UNREAD to "Non lus",
    FilterMode.READ to "Terminés",
)

private enum class GroupSheet { SERIES, TAGS }

/**
 * Rangée de filtres de la Bibliothèque. Séries et Étiquettes ne filtrent pas
 * directement : elles ouvrent la liste de leurs valeurs, chacune menant à son
 * écran de détail — comme le faisait le menu du titre qu'elle remplace.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryFilterChips(
    activeFilter: FilterMode,
    onSelectFilter: (FilterMode) -> Unit,
    series: List<String>,
    seriesCounts: Map<String, Int>,
    tags: List<String>,
    tagCounts: Map<String, Int>,
    onNavigateToSeriesDetail: (String) -> Unit,
    onNavigateToTagDetail: (String) -> Unit,
) {
    var openSheet by remember { mutableStateOf<GroupSheet?>(null) }

    // Bord à bord ; 16 dp d'entrée, alignés sur les couvertures (marge de
    // grille + marge de cellule).
    LazyRow(
        contentPadding = PaddingValues(horizontal = InkToneSpacing.sm * 2, vertical = InkToneSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(InkToneSpacing.sm),
    ) {
        items(DirectFilters, key = { it.first }) { (mode, label) ->
            FilterChip(
                selected = activeFilter == mode,
                onClick = { onSelectFilter(mode) },
                label = { Text(label) },
            )
        }
        if (series.isNotEmpty()) {
            item(key = "series") { GroupChip("Séries") { openSheet = GroupSheet.SERIES } }
        }
        if (tags.isNotEmpty()) {
            item(key = "tags") { GroupChip("Étiquettes") { openSheet = GroupSheet.TAGS } }
        }
    }

    openSheet?.let { sheet ->
        val (title, entries, onNavigate) = when (sheet) {
            GroupSheet.SERIES -> Triple("Séries", series.map { it to (seriesCounts[it] ?: 0) }, onNavigateToSeriesDetail)
            GroupSheet.TAGS -> Triple("Étiquettes", tags.map { it to (tagCounts[it] ?: 0) }, onNavigateToTagDetail)
        }
        ModalBottomSheet(onDismissRequest = { openSheet = null }) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = InkToneSpacing.xl, vertical = InkToneSpacing.sm),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = InkToneSpacing.xxl)) {
                items(entries, key = { it.first }) { (name, count) ->
                    Text(
                        "$name ($count)",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openSheet = null; onNavigate(name) }
                            .padding(horizontal = InkToneSpacing.xl, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupChip(label: String, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label) },
        trailingIcon = { AppIcon(AppSymbol.ArrowDropDown, contentDescription = null) },
    )
}
