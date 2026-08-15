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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material3.DismissibleDrawerSheet
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppIcons
import com.inktone.core.designsystem.AppSymbol
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
    onOpenRecents: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenThemes: () -> Unit = {},
    onOpenSync: () -> Unit = {},
    onOpenOpds: () -> Unit = {},
    onNavigateToSeriesDetail: (String) -> Unit = {},
    onNavigateToTagDetail: (String) -> Unit = {},
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
                    onSelectLibrary = { scope.launch { drawerState.close() } },
                    onOpenRecents = {
                        scope.launch { drawerState.close() }
                        onOpenRecents()
                    },
                    onOpenBookmarks = {
                        scope.launch { drawerState.close() }
                        onOpenBookmarks()
                    },
                    onOpenStats = {
                        scope.launch { drawerState.close() }
                        onOpenStats()
                    },
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    onOpenAbout = {
                        scope.launch { drawerState.close() }
                        onOpenAbout()
                    },
                    onOpenThemes = {
                        scope.launch { drawerState.close() }
                        onOpenThemes()
                    },
                    onOpenSync = {
                        scope.launch { drawerState.close() }
                        onOpenSync()
                    },
                    onOpenOpds = {
                        scope.launch { drawerState.close() }
                        onOpenOpds()
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
                    onLayoutModeChange = { viewModel.onIntent(LibraryIntent.SetLayoutMode(it)) },
                    selectedFormats = state.selectedFormats,
                    onToggleFormat = { viewModel.onIntent(LibraryIntent.ToggleFileFormat(it)) },
                    onClearFormats = { viewModel.onIntent(LibraryIntent.ClearFileFormats) },
                    onRefresh = { viewModel.onIntent(LibraryIntent.Refresh) },
                    onImportClick = onImportClick,
                    activeFilter = state.activeFilter,
                    filterValue = state.filterValue,
                    onSelectFilter = { filter, value -> viewModel.onIntent(LibraryIntent.ChangeFilter(filter, value)) },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    availableSeries = state.availableSeries,
                    seriesCounts = state.seriesCounts,
                    availableTags = state.availableTags,
                    tagCounts = state.tagCounts,
                    onNavigateToSeriesDetail = onNavigateToSeriesDetail,
                    onNavigateToTagDetail = onNavigateToTagDetail,
                )
            },
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                // Lot 5 — résumé de fin d'import ou bannière de progression.
                // Pas de résumé ici quand les détails sont ouverts :
                // ImportResultDetail affiche déjà son propre en-tête résumé
                // (double affichage sinon).
                if (state.importResults.isNotEmpty() && !state.showImportDetails) {
                    ImportResultSummary(
                        results = state.importResults,
                        onDetailsClick = { viewModel.onIntent(LibraryIntent.OpenImportDetails) },
                        onDismiss = { viewModel.onIntent(LibraryIntent.DismissImportResults) },
                    )
                } else if (state.importResults.isEmpty()) {
                    ImportProgressBanner(state.importProgress)
                }

                // Lot 5 — détail des résultats d'import
                if (state.showImportDetails && state.importResults.isNotEmpty()) {
                    ImportResultDetail(
                        results = state.importResults,
                        onOpenPublication = { id ->
                            viewModel.onIntent(LibraryIntent.OpenPublication(id))
                        },
                        onDismiss = { viewModel.onIntent(LibraryIntent.DismissImportResults) },
                    )
                } else when {
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
                        LibraryContent(
                            state = state,
                            onOpen = { id -> viewModel.onIntent(LibraryIntent.OpenPublication(id)) },
                            onToggleFavorite = { id, isFavorite -> viewModel.onIntent(LibraryIntent.ToggleFavorite(id, isFavorite)) },
                            onTogglePin = { id, isPinned -> viewModel.onIntent(LibraryIntent.TogglePin(id, isPinned)) },
                            onDelete = { id -> viewModel.onIntent(LibraryIntent.DeletePublication(id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LibraryDrawerContent(
    onOpenBookmarks: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenRecents: () -> Unit = {},
    onSelectLibrary: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenThemes: () -> Unit = {},
    onOpenSync: () -> Unit = {},
    onOpenOpds: () -> Unit = {},
) {
    Column(Modifier.fillMaxHeight()) {
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
        Column(Modifier.weight(1f).padding(16.dp)) {
        // Récents — Lot 8, en première position des destinations (cible
        // UX). Destination à part entière qui navigue vers un écran
        // dédié : ne JAMAIS reproduire le défaut de l'item mort supprimé
        // au lot 1, dont le onClick posait un filtre sur la Bibliothèque
        // au lieu de naviguer. Icône `AppIcons.Recents` (horloge
        // d'historique) — pas `AppIcons.Loading` (sablier), défaut de
        // l'item historique corrigé par suppression au lot 1.
        NavigationDrawerItem(
            label = { Text("Récents") },
            icon = { AppIcon(AppSymbol.Recents,  contentDescription = null) },
            selected = false,
            onClick = onOpenRecents,
        )
        // Bibliotheque — destination a part entiere, toujours active par
        // defaut : LibraryDrawerContent n'est monte que depuis l'ecran
        // Bibliotheque lui-meme, il n'y a pas d'autre etat possible.
        NavigationDrawerItem(
            label = { Text("Bibliothèque") },
            icon = { AppIcon(AppSymbol.Reading,  contentDescription = null) },
            selected = true,
            onClick = onSelectLibrary,
        )
        NavigationDrawerItem(
            label = { Text("Marque-pages et Notes") },
            icon = { AppIcon(AppSymbol.Bookmark,  contentDescription = null) },
            selected = false,
            onClick = onOpenBookmarks,
        )
        // Lot 13, tâche 13.6 — « Catalogues OPDS » réactivée en b4
        // (ADR-023) : seule destination encore masquée à l'issue du lot 11,
        // désormais navigable vers `CatalogDashboardScreen`. Icône
        // `AppSymbol.Article` (bibliothèque de sources), pas un ajout ad hoc
        // hors du registre Material Symbols.
        NavigationDrawerItem(
            label = { Text("Catalogues OPDS") },
            icon = { AppIcon(AppSymbol.Article,  contentDescription = null) },
            selected = false,
            onClick = onOpenOpds,
        )
        // Lot 11, tâche 11.6 — "Synchronisation" réactivée en b5
        // (UX_FLOW_DESIGN.md §Drawer), entre Marque-pages (b3) et
        // Statistiques (b6) — b4 (Catalogues OPDS) est réactivée par le
        // Lot 13. Retour Issa (vérification) : ce n'est PAS un item de
        // pied de drawer, une première version l'y avait placé à tort.
        NavigationDrawerItem(
            label = { Text("Synchronisation") },
            icon = { AppIcon(AppSymbol.Sync,  contentDescription = null) },
            selected = false,
            onClick = onOpenSync,
        )
        NavigationDrawerItem(
            label = { Text("Statistiques de lecture") },
            icon = { AppIcon(AppSymbol.Stats,  contentDescription = null) },
            selected = false,
            onClick = onOpenStats,
        )

        // ──── #2 Footer drawer ────
        Spacer(Modifier.weight(1f))
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DrawerFooterItem("Paramètres", AppIcons.Settings) { onOpenSettings() }
            // Lot 9 — "Thèmes" réactivé, 3e des 4 destinations masquées au
            // lot 1 (aucune destination affichée sans écran derrière).
            DrawerFooterItem("Thèmes", AppIcons.Appearance) { onOpenThemes() }
            DrawerFooterItem("À propos", AppIcons.Info) { onOpenAbout() }
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
internal fun LibraryTopBar(
    onMenuClick: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOrder: LibrarySortOrder,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
    layoutMode: LibraryLayoutMode,
    onLayoutModeChange: (LibraryLayoutMode) -> Unit,
    selectedFormats: Set<com.inktone.domain.model.PublicationFormat>,
    onToggleFormat: (com.inktone.domain.model.PublicationFormat) -> Unit,
    onClearFormats: () -> Unit,
    onRefresh: () -> Unit,
    onImportClick: () -> Unit,
    // C.4 — filtre actif pour le titre cliquable
    activeFilter: FilterMode = FilterMode.ALL,
    filterValue: String? = null,
    onSelectFilter: (FilterMode, String?) -> Unit = { _, _ -> },
    // Lot 2a.3 — flyout du titre à deux colonnes
    availableSeries: List<String> = emptyList(),
    seriesCounts: Map<String, Int> = emptyMap(),
    availableTags: List<String> = emptyList(),
    tagCounts: Map<String, Int> = emptyMap(),
    onNavigateToSeriesDetail: (String) -> Unit = {},
    onNavigateToTagDetail: (String) -> Unit = {},
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showActionsSheet by remember { mutableStateOf(false) }
    var showNavPopup by remember { mutableStateOf(false) }

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
                    AppIcon(AppSymbol.Back, contentDescription = "Fermer")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    } else {
        // C.4 — Titre cliquable avec le filtre actif
        TopAppBar(
            title = {
                Box {
                    Row(
                        modifier = Modifier.clickable { showNavPopup = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            // UX §Menu déroulant du titre : le titre adaptatif
                            // affiche "Bibliothèque" à la racine (ALL sans
                            // valeur), pas "Tous" — ce dernier reste le libellé
                            // du filtre lui-même dans le flyout (2a.3).
                            filterValue ?: if (activeFilter == FilterMode.ALL) "Bibliothèque" else activeFilter.label(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Icon(
                            AppIcons.ChevronDown,
                            contentDescription = "Changer de vue",
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    LibraryTitleFlyout(
                        expanded = showNavPopup,
                        onDismiss = { showNavPopup = false },
                        series = availableSeries,
                        seriesCounts = seriesCounts,
                        tags = availableTags,
                        tagCounts = tagCounts,
                        onSelectAll = { onSelectFilter(FilterMode.ALL, null) },
                        onSelectFavorites = { onSelectFilter(FilterMode.FAVORITES, null) },
                        onNavigateToSeriesDetail = onNavigateToSeriesDetail,
                        onNavigateToTagDetail = onNavigateToTagDetail,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    AppIcon(AppSymbol.Menu, contentDescription = "Menu")
                }
            },
            actions = {
                IconButton(onClick = { isSearchActive = true }) {
                    AppIcon(AppSymbol.Search, contentDescription = "Rechercher")
                }
                IconButton(onClick = { showFilterDialog = true }) {
                    AppIcon(AppSymbol.Filter,  contentDescription = "Filtrer")
                }
                IconButton(onClick = { showActionsSheet = true }) {
                    AppIcon(AppSymbol.MoreActions, contentDescription = "Actions")
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

    // ──── Popup de filtrage (lot 2a.2) ────
    if (showFilterDialog) {
        LibraryFilterDialog(
            sortOrder = sortOrder,
            onSortOrderChange = onSortOrderChange,
            statusFilter = activeFilter,
            onStatusFilterChange = { onSelectFilter(it, null) },
            layoutMode = layoutMode,
            onLayoutModeChange = onLayoutModeChange,
            selectedFormats = selectedFormats,
            onToggleFormat = onToggleFormat,
            onClearFormats = onClearFormats,
            onDismiss = { showFilterDialog = false },
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
                ActionSheetItem("Actualiser", AppIcons.Refresh) { showActionsSheet = false; onRefresh() }
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

internal fun LibraryLayoutMode.icon() = when (this) {
    LibraryLayoutMode.GRID_COVERS -> AppIcons.CoverOnly
    LibraryLayoutMode.LIST -> AppIcons.ViewList
}

internal fun LibraryLayoutMode.label() = when (this) {
    LibraryLayoutMode.GRID_COVERS -> "Couvertures seules"
    LibraryLayoutMode.LIST -> "Liste"
}

internal fun LibrarySortOrder.label() = when (this) {
    LibrarySortOrder.RECENTLY_ADDED -> "Date d'import"
    LibrarySortOrder.TITLE -> "Titre"
    LibrarySortOrder.AUTHOR -> "Auteur"
    LibrarySortOrder.RECENTLY_OPENED -> "Récents"
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onOpen: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    val resume = state.resumeReadingPublication

    when (state.layoutMode) {
        LibraryLayoutMode.GRID_COVERS -> {
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
                        showTitle = false,
                        progressPercent = state.progressMap[publication.id] ?: 0,
                        onTogglePin = { onTogglePin(publication.id, !publication.isPinned) },
                        onDelete = { onDelete(publication.id) },
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
                        progressPercent = state.progressMap[publication.id] ?: 0,
                        onTogglePin = { onTogglePin(publication.id, !publication.isPinned) },
                        onDelete = { onDelete(publication.id) },
                    )
                }
            }
        }
    }
}

/**
 * Rangée compacte pour le mode Liste — couverture miniature à gauche,
 * titre + auteur à droite, cœur et 3-points côte à côte à l'extrême
 * droite (pas empilés — décision affinée de la cible), barre de
 * progression pleine largeur sous la rangée. `internal` (lot 2a.4) :
 * réutilisée telle quelle par l'écran de détail Séries/Tags, pas de
 * duplication.
 */
@Composable
internal fun PublicationListRow(
    publication: Publication,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    progressPercent: Int = 0,
    onTogglePin: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var showActionsSheet by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        // Alignement en haut, pas au centre : la couverture (102dp) est plus
        // haute que le bloc titre/auteur/cœur/3-points depuis
        // l'agrandissement x1.5 — centré, ce groupe paraissait tassé trop
        // bas par rapport à la couverture (retour device, lot 2b).
        Row(verticalAlignment = Alignment.Top) {
            // Couverture miniature — 72dp de large, ratio 0.7 (x1.5 — bloc par
            // livre jugé trop étroit en vérification device, lot 2b)
            Box(modifier = Modifier.size(width = 72.dp, height = 102.dp)) {
                BookCover(
                    publication = publication,
                    onClick = {},
                    onToggleFavorite = {},
                    showTitle = false,
                    showOverlays = false,
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
                AppIcon(AppSymbol.Favorite, selected = publication.isFavorite,
                    contentDescription = if (publication.isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                    tint = if (publication.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showActionsSheet = true }) {
                AppIcon(AppSymbol.MoreActions,  contentDescription = "Actions sur « ${publication.title} »", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (progressPercent > 0) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(
                    progress = { progressPercent / 100f },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$progressPercent%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }

    if (showActionsSheet) {
        BookActionsSheet(
            publication = publication,
            onDismiss = { showActionsSheet = false },
            onTogglePin = onTogglePin,
            onShowDetails = { showDetailsSheet = true },
            onRequestDelete = { showDeleteConfirm = true },
        )
    }
    if (showDetailsSheet) {
        BookDetailsSheet(publication = publication, onDismiss = { showDetailsSheet = false })
    }
    if (showDeleteConfirm) {
        DeleteConfirmationDialog(
            publicationTitle = publication.title,
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false },
        )
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

// PublicationCard et PublicationListRow remplacés par BookCover (Phase 1b).
// Voir BookCover.kt pour le composant unifié avec Coil, dégradé de repli,
// badge de progression et favori.

// ──── Phase 4 — États vide et erreur ────

/**
 * État bibliothèque vide, textes alignés sur la cible validée (UX
 * §Bibliothèque état vide, lot 2a.6). Texte différent si un import est
 * en cours (l'utilisateur a déjà déclenché un import, pas besoin de lui
 * redemander) — cas absent de la cible, conservé et consigné dans
 * UX_FLOW_DESIGN.md comme ajout plutôt que laissé en zone grise.
 *
 * Illustration : étagère avec emplacements de livres en pointillés,
 * produite au lot 10 (`EmptyLibraryShelfIllustration`) — ferme la dette
 * du lot 2a.6, `AppIcons.Reading` n'est plus un repli.
 */
@Composable
private fun EmptyState(hasActiveImport: Boolean, onImportClick: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyLibraryShelfIllustration(modifier = Modifier.size(width = 160.dp, height = 100.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                if (hasActiveImport) "Import en cours…" else "Votre bibliothèque est vide",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                if (hasActiveImport) "Vos livres apparaîtront ici une fois l'import terminé."
                else "Importez votre premier livre pour commencer à lire et écouter avec InkTone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )
            if (!hasActiveImport) {
                Button(onClick = onImportClick, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Importer votre premier livre")
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

