package com.inktone.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.Publication

/**
 * Écran bibliothèque (Tâche 6.6) — grille virtualisée, performante à
 * 1000+ livres par construction (`LazyVerticalGrid` ne compose que les
 * éléments visibles). Pas de couverture chargée (Coil absent du projet à
 * ce stade — un aplat de couleur portant le titre en attendant).
 *
 * `onNavigateToReader` : câblé par l'appelant (pas encore `MainActivity`,
 * qui n'a aucun graphe de navigation — voir `LibraryEffect`).
 *
 * `floatingActionButton` (Tâche 6.2bis) : point d'intégration du
 * déclencheur d'import (`feature/import`, `ImportPickerButton`) — un
 * slot plutôt qu'une dépendance directe, `feature/library` n'ayant pas le
 * droit de dépendre d'un autre module `feature` (Blueprint §12.4,
 * `checkArchitectureRules`). L'appelant (`app`, qui dépend des deux
 * `feature`) fournit `{ ImportPickerButton() }`.
 */
@Composable
fun LibraryScreen(
    onNavigateToReader: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    floatingActionButton: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LibraryEffect.NavigateToReader -> onNavigateToReader(effect.publicationId)
            }
        }
    }

    Scaffold(floatingActionButton = floatingActionButton) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            FilterRow(
                active = state.activeFilter,
                onSelect = { viewModel.onIntent(LibraryIntent.ChangeFilter(it)) },
            )

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.publications.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Bibliothèque vide — importez un EPUB pour commencer.")
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    // Clé stable = id, jamais l'index — même leçon que la TOC
                    // (Tâche 4.11, crash LazyColumn à clé non unique).
                    gridItems(state.publications, key = { it.id }) { publication ->
                        PublicationCard(
                            publication = publication,
                            onClick = { viewModel.onIntent(LibraryIntent.OpenPublication(publication.id)) },
                        )
                    }
                }
            }
        }
    }
}

// Modes sans valeur associee uniquement (SERIES/TAG/BY_AUTHOR exigeraient
// un selecteur de valeurs distinctes en base, hors perimetre de la
// bibliotheque basique - voir LibraryUiState).
private val SelectableFilters = listOf(FilterMode.ALL, FilterMode.FAVORITES, FilterMode.UNREAD, FilterMode.IN_PROGRESS, FilterMode.READ)

private fun FilterMode.label() = when (this) {
    FilterMode.ALL -> "Tous"
    FilterMode.FAVORITES -> "Favoris"
    FilterMode.UNREAD -> "Non lus"
    FilterMode.IN_PROGRESS -> "En cours"
    FilterMode.READ -> "Terminés"
    FilterMode.SERIES, FilterMode.TAG, FilterMode.BY_AUTHOR -> name
}

@Composable
private fun FilterRow(active: FilterMode, onSelect: (FilterMode) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        rowItems(SelectableFilters, key = { it.name }) { filter ->
            FilterChip(
                selected = filter == active,
                onClick = { onSelect(filter) },
                label = { Text(filter.label()) },
            )
        }
    }
}

@Composable
private fun PublicationCard(publication: Publication, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .aspectRatio(0.7f)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            text = publication.title,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
