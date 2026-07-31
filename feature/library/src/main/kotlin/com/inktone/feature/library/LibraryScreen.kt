package com.inktone.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DismissibleDrawerSheet
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    floatingAudioButton: @Composable () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenThemePicker: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Phase 4 — rafraîchissement au retour du Reader (ON_RESUME)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LibraryEffect.NavigateToReader -> onNavigateToReader(effect.publicationId)
                is LibraryEffect.NavigateToStats -> onOpenStats()
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
                    onOpenStats = {
                        scope.launch { drawerState.close() }
                        onOpenStats()
                    },
                    onOpenAbout = {
                        scope.launch { drawerState.close() }
                        onOpenAbout()
                    },
                    onOpenThemePicker = {
                        scope.launch { drawerState.close() }
                        onOpenThemePicker()
                    },
                )
            }
        },
    ) {
        Scaffold(
            floatingActionButton = {
                Row(verticalAlignment = Alignment.Bottom) {
                    floatingAudioButton()
                    Spacer(Modifier.width(12.dp))
                    floatingActionButton()
                }
            },
            topBar = {
                LibraryTopBar(
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = { viewModel.onIntent(LibraryIntent.SetSearchQuery(it)) },
                    sortOrder = state.sortOrder,
                    onSortOrderChange = { viewModel.onIntent(LibraryIntent.SetSortOrder(it)) },
                    layoutMode = state.layoutMode,
                    onCycleLayout = { viewModel.onIntent(LibraryIntent.CycleLayout) },
                    onRefresh = { viewModel.onIntent(LibraryIntent.Refresh) },
                    onImportClick = onImportClick,
                    onOpenAbout = onOpenAbout,
                    onOpenThemePicker = onOpenThemePicker,
                    onMenuClick = { scope.launch { drawerState.open() } },
                )
            },
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                ImportProgressBanner(state.importProgress)
                FilterRow(
                    active = state.activeFilter,
                    onSelect = { viewModel.onIntent(LibraryIntent.ChangeFilter(it)) },
                )
                TagsFilterBar(
                    tags = state.availableTags,
                    activeFilter = state.activeFilter,
                    activeValue = state.filterValue,
                    onSelect = { viewModel.onIntent(LibraryIntent.ChangeFilter(FilterMode.TAG, it)) },
                )

                when {
                    state.isLoading -> LibraryShimmerGrid()
                    state.errorMessage != null -> ErrorState(
                        message = state.errorMessage!!,
                        onRetry = { viewModel.onIntent(LibraryIntent.Refresh) },
                        onDismiss = { viewModel.onIntent(LibraryIntent.DismissError) },
                    )
                    state.displayedPublications.isEmpty() -> EmptyState(
                        hasActiveImport = state.importProgress.total > 0 || state.importProgress.hasQueuedChunks,
                        onImportClick = onImportClick,
                    )
                    else -> {
                        // Vue groupée par séries — uniquement en mode ALL
                        if (state.activeFilter == FilterMode.ALL && state.availableSeries.isNotEmpty()) {
                            SeriesGroupedView(
                                publications = state.displayedPublications,
                                onOpen = { id -> viewModel.onIntent(LibraryIntent.OpenPublication(id)) },
                                onToggleFavorite = { id, fav -> viewModel.onIntent(LibraryIntent.ToggleFavorite(id, fav)) },
                                onSelectSeries = { series -> viewModel.onIntent(LibraryIntent.ChangeFilter(FilterMode.SERIES, series)) },
                            )
                        }
                        LibraryContent(
                            state = state,
                            onOpen = { id -> viewModel.onIntent(LibraryIntent.OpenPublication(id)) },
                            onToggleFavorite = { id, isFavorite -> viewModel.onIntent(LibraryIntent.ToggleFavorite(id, isFavorite)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryDrawerContent(
    state: LibraryUiState,
    onSelectFilter: (FilterMode, String?) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenThemePicker: () -> Unit = {},
) {
    Column {
        // C.1 — Header avec dégradé brand (legacy §1.2)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.primary,
                        )
                    )
                )
                .padding(start = 24.dp, bottom = 24.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                "InkTone",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.padding(16.dp)) {
        SelectableFilters.forEach { filter ->
            NavigationDrawerItem(
                label = { Text(filter.label()) },
                selected = state.activeFilter == filter,
                onClick = { onSelectFilter(filter, null) },
            )
        }
        NavigationDrawerItem(
            label = { Text("Récents") },
            icon = { Icon(AppIcons.Loading, contentDescription = null) },
            selected = state.activeFilter == FilterMode.ALL && state.sortOrder == LibrarySortOrder.RECENTLY_OPENED,
            onClick = { onSelectFilter(FilterMode.ALL, null) },
        )
        NavigationDrawerItem(
            label = { Text("Statistiques") },
            icon = { Icon(AppIcons.Stats, contentDescription = null) },
            selected = false,
            onClick = onOpenStats,
        )
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
        if (state.availableAuthors.isNotEmpty()) {
            Text("Auteurs", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            state.availableAuthors.forEach { author ->
                NavigationDrawerItem(
                    label = { Text(author) },
                    selected = state.activeFilter == FilterMode.BY_AUTHOR && state.filterValue == author,
                    onClick = { onSelectFilter(FilterMode.BY_AUTHOR, author) },
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

        // ──── #2 Footer drawer ────
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DrawerFooterItem("À propos", AppIcons.Info) { onOpenAbout() }
            DrawerFooterItem("Thème", AppIcons.Appearance) { onOpenThemePicker() }
            // C.2 — Debug conditionné (cohérent avec BootstrapAndOpenFixture, Phase 0)
            if (BuildConfig.DEBUG) {
                DrawerFooterItem("Debug", AppIcons.Data) { /* no-op pour l'instant */ }
            }
        }
        } // Column content
    } // Column root
}

@Composable
private fun DrawerFooterItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ──── #7 TopBar primary + #1 SearchBar collapsible + #4 BottomSheet ────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    onMenuClick: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOrder: LibrarySortOrder,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
    layoutMode: LibraryLayoutMode,
    onCycleLayout: () -> Unit,
    onRefresh: () -> Unit,
    onImportClick: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenThemePicker: () -> Unit = {},
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var isSortMenuExpanded by remember { mutableStateOf(false) }
    var showActionsSheet by remember { mutableStateOf(false) }

    if (isSearchActive) {
        // État recherche : SearchBar pleine largeur qui remplace la TopBar
        TopAppBar(
            title = {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Rechercher titre ou auteur") },
                    singleLine = true,
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    isSearchActive = false
                    onSearchQueryChange("")
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fermer")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    } else {
        // État normal : icônes d'action
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Menu")
                }
            },
            actions = {
                IconButton(onClick = { isSearchActive = true }) {
                    Icon(Icons.Outlined.Search, contentDescription = "Rechercher")
                }
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
                    Icon(layoutMode.icon(), contentDescription = layoutMode.label())
                }
                IconButton(onClick = { showActionsSheet = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Actions")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }

    // ──── #4 Menu 3-points → ModalBottomSheet ────
    if (showActionsSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showActionsSheet = false },
            sheetState = sheetState,
        ) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Text("Bibliothèque", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary)
                ActionSheetItem("Importer", AppIcons.Data) { showActionsSheet = false; onImportClick() }
                ActionSheetItem("Actualiser", AppIcons.Loading) { showActionsSheet = false; onRefresh() }
                // C.3 — Régénérer et réinitialiser les couvertures
                ActionSheetItem("Régénérer les couvertures", AppIcons.Hint) {
                    showActionsSheet = false
                    // TODO: viewModel.onIntent(LibraryIntent.RegenerateCovers)
                }
                ActionSheetItem("Réinitialiser les couvertures", AppIcons.ErrorOutlined) {
                    showActionsSheet = false
                    // TODO: dialogue confirmation + viewModel.onIntent(LibraryIntent.ResetCovers)
                }
                // C.2 — À propos et Réglages du thème dans le menu 3-points
                ActionSheetItem("À propos", AppIcons.Info) { showActionsSheet = false; onOpenAbout() }
                ActionSheetItem("Thème", AppIcons.Appearance) { showActionsSheet = false; onOpenThemePicker() }
            }
        }
    }
}

@Composable
private fun ActionSheetItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
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
                        progressPercent = state.progressMap[publication.id] ?: 0,
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

// ──── Phase 4 — États vide et erreur ────

/**
 * État bibliothèque vide avec illustration et bouton d'import direct.
 * Texte différent si un import est en cours (l'utilisateur a déjà
 * déclenché un import, pas besoin de lui redemander).
 */
@Composable
private fun EmptyState(hasActiveImport: Boolean, onImportClick: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                AppIcons.Reading,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Text(
                if (hasActiveImport) "Import en cours…" else "Bibliothèque vide",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                if (hasActiveImport) "Vos livres apparaîtront ici une fois l'import terminé."
                else "Importez un EPUB pour commencer votre bibliothèque.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )
            if (!hasActiveImport) {
                Button(onClick = onImportClick, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Importer des livres")
                }
            }
        }
    }
}

/**
 * Bannière d'erreur avec message et bouton « Réessayer ».
 * Affichée quand [LibraryUiState.errorMessage] est non-null.
 */
@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                AppIcons.Error,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                "Erreur",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
                OutlinedButton(onClick = onDismiss) { Text("Ignorer") }
                Button(onClick = onRetry) { Text("Réessayer") }
            }
        }
    }
}

// ──── Phase 2 — Composants de navigation enrichie ────

/**
 * Barre de tags horizontale affichée sous la [FilterRow], hors du drawer.
 * Visible uniquement si des tags existent (legacy : [TagsFilterBar]).
 */
@Composable
private fun TagsFilterBar(
    tags: List<String>,
    activeFilter: FilterMode,
    activeValue: String?,
    onSelect: (String) -> Unit,
) {
    if (tags.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        rowItems(tags, key = { it }) { tag ->
            FilterChip(
                selected = activeFilter == FilterMode.TAG && activeValue == tag,
                onClick = { onSelect(tag) },
                label = { Text(tag) },
            )
        }
    }
}

/**
 * Vue groupée par séries — chaque série a un en-tête et une rangée
 * horizontale scrollable de couvertures à largeur fixe (110dp).
 * Affichée uniquement en mode ALL au-dessus de la grille principale.
 *
 * E.4 — N'utilise plus LazyColumn dans un Column scrollable (nested
 * scroll non défini sur Android). Itère avec forEach dans un Column
 * simple — la liste de séries est bornée, un LazyColumn n'est pas
 * nécessaire.
 */
@Composable
private fun SeriesGroupedView(
    publications: List<Publication>,
    onOpen: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onSelectSeries: (String) -> Unit,
) {
    val grouped = publications
        .filter { it.seriesName != null }
        .groupBy { it.seriesName!! }

    if (grouped.isEmpty()) return

    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        grouped.forEach { (series, books) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSeries(series) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    series,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                Text(
                    "${books.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                rowItems(books, key = { "series-${it.id}" }) { book ->
                    BookCover(
                        publication = book,
                        onClick = { onOpen(book.id) },
                        onToggleFavorite = { onToggleFavorite(book.id, !book.isFavorite) },
                        modifier = Modifier.width(110.dp),
                        showTitle = true,
                    )
                }
            }
        }
    }
}
