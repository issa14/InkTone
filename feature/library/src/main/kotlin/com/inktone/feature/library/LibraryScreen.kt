package com.inktone.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DismissibleDrawerSheet
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.AppIcons
import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.Publication
import com.inktone.domain.service.ImportProgress
import kotlinx.coroutines.launch

/**
 * Tache 9bis.4 — reconstruction complete : drawer (filtres/series/tags),
 * recherche titre/auteur integree, tri, bascule grille/liste, carte
 * "reprendre la lecture" proeminente, favoris, chargement shimmer.
 * Remplace la grille nue de la Tache 6.6.
 *
 * `onNavigateToReader` : câblé par l'appelant (`InkToneNavHost`, Tâche
 * 9bis.2). `floatingActionButton` (Tâche 6.2bis) : point d'intégration de
 * l'import (`feature/import`, slot plutôt qu'une dépendance directe,
 * `feature/library` n'ayant pas le droit de dépendre d'un autre module
 * `feature`, Blueprint §12.4).
 *
 * Transition de contenu partagee (couverture -> Reader, `SharedTransitionLayout`)
 * volontairement PAS implementee ici : necessiterait de faire passer un
 * `SharedTransitionScope` a travers tout `InkToneNavHost` (changement
 * invasif) pour un rendu que cette session ne peut pas verifier
 * visuellement (pas d'emulateur/device disponible) - reporte plutot que
 * livre sans verification reelle (Blueprint §17.2, le code fait foi).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToReader: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    floatingActionButton: @Composable () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LibraryEffect.NavigateToReader -> onNavigateToReader(effect.publicationId)
            }
        }
    }

    DismissibleNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DismissibleDrawerSheet {
                LibraryDrawerContent(
                    state = state,
                    onSelectFilter = { filter, value ->
                        viewModel.onIntent(LibraryIntent.ChangeFilter(filter, value))
                        scope.launch { drawerState.close() }
                    },
                    onOpenBookmarks = {
                        scope.launch { drawerState.close() }
                        onOpenBookmarks()
                    },
                )
            }
        },
    ) {
        Scaffold(floatingActionButton = floatingActionButton) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                ImportProgressBanner(state.importProgress)
                LibraryToolbar(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = { viewModel.onIntent(LibraryIntent.SetSearchQuery(it)) },
                    sortOrder = state.sortOrder,
                    onSortOrderChange = { viewModel.onIntent(LibraryIntent.SetSortOrder(it)) },
                    layoutMode = state.layoutMode,
                    onCycleLayout = { viewModel.onIntent(LibraryIntent.CycleLayout) },
                )
                FilterRow(
                    active = state.activeFilter,
                    onSelect = { viewModel.onIntent(LibraryIntent.ChangeFilter(it)) },
                )

                when {
                    state.isLoading -> LibraryShimmerGrid()
                    state.displayedPublications.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (state.searchQuery.isBlank()) "Bibliothèque vide — importez un EPUB pour commencer." else "Aucun résultat pour « ${state.searchQuery} ».")
                    }
                    else -> LibraryContent(
                        state = state,
                        onOpen = { id -> viewModel.onIntent(LibraryIntent.OpenPublication(id)) },
                        onToggleFavorite = { id, isFavorite -> viewModel.onIntent(LibraryIntent.ToggleFavorite(id, isFavorite)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryDrawerContent(state: LibraryUiState, onSelectFilter: (FilterMode, String?) -> Unit, onOpenBookmarks: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text("Bibliothèque", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        SelectableFilters.forEach { filter ->
            NavigationDrawerItem(
                label = { Text(filter.label()) },
                selected = state.activeFilter == filter,
                onClick = { onSelectFilter(filter, null) },
            )
        }
        NavigationDrawerItem(
            label = { Text("Signets") },
            icon = { Icon(AppIcons.Bookmark, contentDescription = null) },
            selected = false,
            onClick = onOpenBookmarks,
        )
        if (state.availableSeries.isNotEmpty()) {
            Text("Séries", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            state.availableSeries.forEach { series ->
                NavigationDrawerItem(
                    label = { Text(series) },
                    selected = state.activeFilter == FilterMode.SERIES && state.filterValue == series,
                    onClick = { onSelectFilter(FilterMode.SERIES, series) },
                )
            }
        }
        if (state.availableTags.isNotEmpty()) {
            Text("Tags", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems(state.availableTags, key = { it }) { tag ->
                    FilterChip(
                        selected = state.activeFilter == FilterMode.TAG && state.filterValue == tag,
                        onClick = { onSelectFilter(FilterMode.TAG, tag) },
                        label = { Text(tag) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryToolbar(
    onMenuClick: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOrder: LibrarySortOrder,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
    layoutMode: LibraryLayoutMode,
    onCycleLayout: () -> Unit,
) {
    var isSortMenuExpanded by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Outlined.Menu, contentDescription = "Filtres et séries")
        }
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Rechercher titre ou auteur") },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
            singleLine = true,
        )
        Box {
            IconButton(onClick = { isSortMenuExpanded = true }) {
                Icon(Icons.Outlined.Sort, contentDescription = "Trier")
            }
            DropdownMenu(expanded = isSortMenuExpanded, onDismissRequest = { isSortMenuExpanded = false }) {
                LibrarySortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(order.label()) },
                        onClick = { onSortOrderChange(order); isSortMenuExpanded = false },
                    )
                }
            }
        }
        IconButton(onClick = onCycleLayout) {
            Icon(
                imageVector = layoutMode.icon(),
                contentDescription = layoutMode.label(),
            )
        }
    }
}

private fun LibraryLayoutMode.icon() = when (this) {
    LibraryLayoutMode.GRID -> AppIcons.ViewGrid
    LibraryLayoutMode.GRID_COVERS -> AppIcons.CoverOnly
    LibraryLayoutMode.LIST -> AppIcons.ViewList
}

private fun LibraryLayoutMode.label() = when (this) {
    LibraryLayoutMode.GRID -> "Grille"
    LibraryLayoutMode.GRID_COVERS -> "Couvertures seules"
    LibraryLayoutMode.LIST -> "Liste"
}

private fun LibrarySortOrder.label() = when (this) {
    LibrarySortOrder.TITLE -> "Titre"
    LibrarySortOrder.RECENTLY_ADDED -> "Ajout récent"
    LibrarySortOrder.RECENTLY_OPENED -> "Lecture récente"
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onOpen: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
) {
    val resume = state.resumeReadingPublication

    when (state.layoutMode) {
        LibraryLayoutMode.GRID, LibraryLayoutMode.GRID_COVERS -> {
            val showTitle = state.layoutMode == LibraryLayoutMode.GRID
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = PaddingValues(8.dp),
            ) {
                if (resume != null) {
                    gridItems(listOf(resume), key = { "resume-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { publication ->
                        ResumeReadingCard(publication, onClick = { onOpen(publication.id) })
                    }
                }
                gridItems(state.displayedPublications, key = { it.id }) { publication ->
                    BookCover(
                        publication = publication,
                        onClick = { onOpen(publication.id) },
                        onToggleFavorite = { onToggleFavorite(publication.id, !publication.isFavorite) },
                        modifier = Modifier.padding(8.dp),
                        showTitle = showTitle,
                    )
                }
            }
        }

        LibraryLayoutMode.LIST -> {
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                if (resume != null) {
                    item { ResumeReadingCard(resume, onClick = { onOpen(resume.id) }) }
                }
                listItems(state.displayedPublications, key = { it.id }) { publication ->
                    PublicationListRow(
                        publication = publication,
                        onClick = { onOpen(publication.id) },
                        onToggleFavorite = { onToggleFavorite(publication.id, !publication.isFavorite) },
                    )
                }
            }
        }
    }
}

/** Rangée compacte pour le mode Liste — couverture miniature à gauche, titre + auteur à droite. */
@Composable
private fun PublicationListRow(
    publication: Publication,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Couverture miniature — 48dp de large, ratio 0.7
        Box(modifier = Modifier.size(width = 48.dp, height = 68.dp)) {
            BookCover(
                publication = publication,
                onClick = {},
                onToggleFavorite = {},
                showTitle = false,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                publication.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (publication.authors.isNotEmpty()) {
                Text(
                    publication.authors.joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (publication.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (publication.isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                tint = if (publication.isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Tache 9bis.4 — proeminente en tete de grille, pas seulement un FAB flottant discret (legacy). */
@Composable
private fun ResumeReadingCard(publication: Publication, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Reprendre la lecture", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(publication.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (publication.authors.isNotEmpty()) {
                Text(publication.authors.joinToString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Modes sans valeur associee uniquement (SERIES/TAG/BY_AUTHOR ont leur
// propre point d'entree dans le drawer, avec la valeur exacte requise).
private val SelectableFilters = listOf(FilterMode.ALL, FilterMode.FAVORITES, FilterMode.UNREAD, FilterMode.IN_PROGRESS, FilterMode.READ)

private fun FilterMode.label() = when (this) {
    FilterMode.ALL -> "Tous"
    FilterMode.FAVORITES -> "Favoris"
    FilterMode.UNREAD -> "Non lus"
    FilterMode.IN_PROGRESS -> "En cours"
    FilterMode.READ -> "Terminés"
    FilterMode.SERIES, FilterMode.TAG, FilterMode.BY_AUTHOR -> name
}

/**
 * Cachée par défaut (`total == 0 && !hasQueuedChunks`, l'état initial de
 * [ImportProgress]). Progression déterminée pour le lot en cours ;
 * indéterminée (`LinearProgressIndicator` sans valeur) quand d'autres
 * lots suivent mais qu'aucun n'est encore `RUNNING` — limitation
 * documentée sur [ImportProgress] (WorkManager n'expose pas la taille
 * d'un lot pas encore démarré).
 */
@Composable
private fun ImportProgressBanner(progress: ImportProgress) {
    if (progress.total == 0 && !progress.hasQueuedChunks) return

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        if (progress.total > 0) {
            LinearProgressIndicator(
                progress = { progress.current.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Import : ${progress.current} / ${progress.total}")
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Import en attente…")
        }
    }
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

// PublicationCard et PublicationListRow remplacés par BookCover (Phase 1b).
// Voir BookCover.kt pour le composant unifié avec Coil, dégradé de repli,
// badge de progression et favori.
