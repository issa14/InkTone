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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import com.inktone.core.designsystem.InkToneBrandMark
import com.inktone.core.designsystem.NarrativeAccentFamily
import com.inktone.core.designsystem.InkToneShapes
import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.Publication
import com.inktone.domain.model.cleanedAuthorsForDisplay
import com.inktone.domain.model.cleanedForDisplay
import com.inktone.domain.service.ImportProgress
import com.inktone.domain.service.SyncOperationResult
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
    onNavigateToReader: (publicationId: String, autoStartTts: Boolean) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    floatingActionButton: @Composable () -> Unit = {},
    floatingAudioButton: @Composable () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onImportClick: () -> Unit = {},
    // Lot 18 — le drawer est hoisté dans InkToneNavHost (partagé par les
    // 6 destinations principales) : cet écran ne porte plus que le bouton
    // hamburger, les callbacks de destinations vivent avec le drawer.
    onMenuClick: () -> Unit = {},
    onNavigateToSeriesDetail: (String) -> Unit = {},
    onNavigateToTagDetail: (String) -> Unit = {},
    // Lot 19 — « Synchroniser avec le cloud » non configuré bascule vers
    // l'écran de configuration de la sync (UX §Bottom sheet 3-points).
    onOpenSync: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Aucun rafraîchissement au retour d'un autre écran : la grille et ses
    // badges de progression dérivent de deux flux Room combinés
    // (`LibraryViewModel.observePublications`) et se mettent à jour seuls. Le
    // rejeu qui vivait ici reposait `isLoading` et faisait clignoter la grille
    // entière en shimmer à chaque retour du Lecteur.

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LibraryEffect.NavigateToReader -> onNavigateToReader(effect.publicationId, effect.autoStartTts)
                is LibraryEffect.NavigateToStats -> onOpenStats()
                is LibraryEffect.NavigateToSync -> onOpenSync()
                is LibraryEffect.CoversRegenerated -> {
                    val succeeded = effect.result.processed - effect.result.failed
                    scope.launch {
                        snackbarHostState.showSnackbar("Couvertures reconstruites ($succeeded/${effect.result.processed})")
                    }
                }
                is LibraryEffect.CoversReset -> scope.launch {
                    snackbarHostState.showSnackbar("Couvertures réinitialisées")
                }
                is LibraryEffect.RandomBookUnavailable -> scope.launch {
                    snackbarHostState.showSnackbar("Aucun livre à ouvrir")
                }
                is LibraryEffect.SyncCompleted -> scope.launch {
                    val message = when (effect.result) {
                        is SyncOperationResult.Success -> "Synchronisation terminée"
                        is SyncOperationResult.Failed -> "Échec de la synchronisation"
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                onImportClick = onImportClick,
                activeFilter = state.activeFilter,
                filterValue = state.filterValue,
                onSelectFilter = { filter, value -> viewModel.onIntent(LibraryIntent.ChangeFilter(filter, value)) },
                onMenuClick = onMenuClick,
                availableSeries = state.availableSeries,
                seriesCounts = state.seriesCounts,
                availableTags = state.availableTags,
                tagCounts = state.tagCounts,
                onNavigateToSeriesDetail = onNavigateToSeriesDetail,
                onNavigateToTagDetail = onNavigateToTagDetail,
                // Lot 19 — actions du menu 3-points
                onOpenRandomBook = { viewModel.onIntent(LibraryIntent.OpenRandomBook) },
                onSyncNow = { viewModel.onIntent(LibraryIntent.SyncNow) },
                onResetCovers = { viewModel.onIntent(LibraryIntent.ResetCovers) },
                onRegenerateCovers = { viewModel.onIntent(LibraryIntent.RegenerateCovers) },
                isRegeneratingCovers = state.isRegeneratingCovers,
                coverRegeneration = state.coverRegeneration,
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
                    searchQuery = state.searchQuery,
                )
                else -> {
                    // Carte « Reprendre la lecture » fixe au-dessus de la liste :
                    // reste visible en permanence, seule la bibliothèque défile
                    // dessous (au lieu d'un premier item qui disparaissait au scroll).
                    state.resumeReadingPublication?.let { resume ->
                        ResumeReadingCard(
                            publication = resume,
                            progressPercent = state.progressMap[resume.id] ?: 0,
                            onClick = { viewModel.onIntent(LibraryIntent.OpenPublication(resume.id)) },
                            isNarrating = state.isResumeNarrationPlaying,
                            onTogglePlayback = { viewModel.onIntent(LibraryIntent.ToggleResumeNarration(resume.id)) },
                        )
                    }
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

@Composable
fun LibraryDrawerContent(
    onOpenBookmarks: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenRecents: () -> Unit = {},
    onSelectLibrary: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenThemes: () -> Unit = {},
    onOpenSync: () -> Unit = {},
    onOpenOpds: () -> Unit = {},
    selected: DrawerDestination = DrawerDestination.LIBRARY,
) {
    Column(Modifier.fillMaxHeight()) {
        // En-tête de marque, sur `surface` — la même couleur que le corps du
        // menu, donc sans couture.
        //
        // Remplace un bloc de 140 dp en dégradé `primaryContainer` →
        // `primary`. Ce dégradé descendait du CLAIR vers le SOMBRE : son
        // point le plus sombre tombait donc contre le corps du menu, quasi
        // blanc, plaçant le contraste maximal exactement sur la couture.
        // Depuis que la barre de statut suit la barre du haut, la colonne
        // empilait quatre bandes avec deux inversions (système sombre →
        // lavande clair → violet sombre → blanc cassé).
        //
        // « InkTone » est en Literata (`NarrativeAccentFamily`) et non dans
        // la scale Work Sans : c'est la police d'accent narratif de la
        // marque, déjà employée pour ce même mot par l'Onboarding
        // (« Bienvenue sur InkTone »). Elle est posée ICI, au point d'appel,
        // jamais dans `InkToneTypography` — `TypographyBrandTest` l'interdit
        // dans la scale.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkToneBrandMark(modifier = Modifier.size(44.dp))
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    "InkTone",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = NarrativeAccentFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Votre compagnon de voyage",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Symétrique du séparateur qui coiffe déjà le pied de drawer : sans
        // elle, la liste de navigation est bornée en bas et pas en haut.
        HorizontalDivider()
        Column(Modifier.weight(1f).padding(16.dp)) {
        // Récents — Lot 8, en première position des destinations (cible
        // UX). Destination à part entière qui navigue vers un écran
        // dédié : ne JAMAIS reproduire le défaut de l'item mort supprimé
        // au lot 1, dont le onClick posait un filtre sur la Bibliothèque
        // au lieu de naviguer. Icône `AppSymbol.Recents` (horloge
        // d'historique) — pas `AppSymbol.Loading` (sablier), défaut de
        // l'item historique corrigé par suppression au lot 1.
        NavigationDrawerItem(
            label = { Text("Récents") },
            icon = { AppIcon(AppSymbol.Recents,  contentDescription = null) },
            selected = selected == DrawerDestination.RECENTS,
            onClick = onOpenRecents,
        )
        // Bibliotheque — destination a part entiere. Lot 18 : le drawer
        // etant hoiste dans InkToneNavHost et partage par les 6
        // destinations principales, l'item actif est desormais derive de
        // la destination reelle ([selected]), plus un `true` fige.
        NavigationDrawerItem(
            label = { Text("Bibliothèque") },
            icon = { AppIcon(AppSymbol.Library, contentDescription = null) },
            selected = selected == DrawerDestination.LIBRARY,
            onClick = onSelectLibrary,
        )
        NavigationDrawerItem(
            label = { Text("Marque-pages et Notes") },
            icon = { AppIcon(AppSymbol.Bookmark,  contentDescription = null) },
            selected = selected == DrawerDestination.BOOKMARKS,
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
            selected = selected == DrawerDestination.OPDS,
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
            selected = selected == DrawerDestination.SYNC,
            onClick = onOpenSync,
        )
        NavigationDrawerItem(
            label = { Text("Statistiques de lecture") },
            icon = { AppIcon(AppSymbol.Stats,  contentDescription = null) },
            selected = selected == DrawerDestination.STATISTICS,
            onClick = onOpenStats,
        )

        // ──── #2 Footer drawer ────
        Spacer(Modifier.weight(1f))
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DrawerFooterItem("Paramètres", AppSymbol.Settings) { onOpenSettings() }
            // Lot 9 — "Thèmes" réactivé, 3e des 4 destinations masquées au
            // lot 1 (aucune destination affichée sans écran derrière).
            DrawerFooterItem("Thèmes", AppSymbol.Theme) { onOpenThemes() }
            DrawerFooterItem("À propos", AppSymbol.Info) { onOpenAbout() }
        }
        } // Column content
    } // Column root
}

@Composable
private fun DrawerFooterItem(label: String, icon: AppSymbol, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp),
    ) {
        AppIcon(icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    // Lot 19 — actions du menu 3-points
    onOpenRandomBook: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onResetCovers: () -> Unit = {},
    onRegenerateCovers: () -> Unit = {},
    isRegeneratingCovers: Boolean = false,
    coverRegeneration: CoverRegenerationProgress? = null,
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showActionsSheet by remember { mutableStateOf(false) }
    var showNavPopup by remember { mutableStateOf(false) }
    var showResetCoversConfirm by remember { mutableStateOf(false) }
    // Lot 19 — la reconstruction garde le bottom sheet OUVERT pour que la
    // progression X/Y soit réellement visible ; il se referme à la fin.
    var regenerationRequested by remember { mutableStateOf(false) }

    LaunchedEffect(isRegeneratingCovers) {
        if (!isRegeneratingCovers && regenerationRequested) {
            showActionsSheet = false
            regenerationRequested = false
        }
    }

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
                            // `titleLarge` comme les autres destinations : elles
                            // laissent `TopAppBar` appliquer son style par
                            // défaut, cet écran forçait `titleMedium` et son
                            // titre paraissait d'un quart plus petit (16 sp
                            // contre 20) alors qu'il est de même rang.
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        // Unique indice que le titre ouvre un menu : il doit se
                        // voir. Un chevron à 16 dp et 50 % d'opacité se
                        // réduisait à un filet à côté d'un titre semi-gras. Le
                        // défaut n'était PAS le contraste — mesuré à 4,58:1,
                        // au-dessus du seuil WCAG de 3:1 pour un élément
                        // d'interface — mais le POIDS visuel : un trait `wght
                        // 400` rapetissé ne tient pas à côté des jambages du
                        // texte. Un triangle plein garde sa masse quelle que
                        // soit la taille, et c'est l'affordance attendue d'un
                        // déroulant.
                        AppIcon(
                            AppSymbol.ArrowDropDown,
                            contentDescription = "Changer de vue",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
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
                ActionSheetItem("Importer", AppSymbol.Data) { showActionsSheet = false; onImportClick() }
                ActionSheetItem("Couverture par défaut", AppSymbol.CoverOnly) {
                    showActionsSheet = false
                    showResetCoversConfirm = true
                }
                if (isRegeneratingCovers) {
                    ActionSheetProgress("Reconstruire les couvertures", coverRegeneration)
                } else {
                    ActionSheetItem("Reconstruire les couvertures", AppSymbol.Refresh) {
                        // Le sheet reste ouvert : la progression X/Y est
                        // visible dans l'item, refermé à la fin par
                        // LaunchedEffect(isRegeneratingCovers).
                        regenerationRequested = true
                        onRegenerateCovers()
                    }
                }
                ActionSheetItem("Ouvrir un livre au hasard", AppSymbol.Reading) { showActionsSheet = false; onOpenRandomBook() }
                ActionSheetItem("Synchroniser avec le cloud", AppSymbol.Sync) { showActionsSheet = false; onSyncNow() }
            }
        }
    }

    if (showResetCoversConfirm) {
        AlertDialog(
            onDismissRequest = { showResetCoversConfirm = false },
            title = { Text("Réinitialiser les couvertures ?") },
            text = { Text("Toutes les couvertures reviendront à leur apparence par défaut. Vous pourrez les reconstruire à tout moment.") },
            confirmButton = {
                TextButton(onClick = { showResetCoversConfirm = false; onResetCovers() }) {
                    Text("Réinitialiser", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetCoversConfirm = false }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun ActionSheetItem(label: String, icon: AppSymbol, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Lot 19 — item non cliquable affichant la progression live X/Y pendant la reconstruction. */
@Composable
private fun ActionSheetProgress(label: String, progress: CoverRegenerationProgress?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(16.dp))
        Text(
            if (progress != null) "$label (${progress.processed}/${progress.total})" else label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

internal fun LibraryLayoutMode.icon() = when (this) {
    LibraryLayoutMode.GRID_COVERS -> AppSymbol.CoverOnly
    LibraryLayoutMode.GRID_DETAILED -> AppSymbol.ViewGrid
    LibraryLayoutMode.LIST -> AppSymbol.ViewList
}

internal fun LibraryLayoutMode.label() = when (this) {
    LibraryLayoutMode.GRID_COVERS -> "Couvertures seules"
    LibraryLayoutMode.GRID_DETAILED -> "Grille détaillée"
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
    when (state.layoutMode) {
        LibraryLayoutMode.GRID_COVERS -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = PaddingValues(8.dp),
            ) {
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

        LibraryLayoutMode.GRID_DETAILED -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = PaddingValues(8.dp),
            ) {
                gridItems(state.displayedPublications, key = { it.id }) { publication ->
                    BookGridCell(
                        publication = publication,
                        onClick = { onOpen(publication.id) },
                        onToggleFavorite = { onToggleFavorite(publication.id, !publication.isFavorite) },
                        progressPercent = state.progressMap[publication.id] ?: 0,
                        onTogglePin = { onTogglePin(publication.id, !publication.isPinned) },
                        onDelete = { onDelete(publication.id) },
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }

        LibraryLayoutMode.LIST -> {
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
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
 * Cellule du mode « Grille détaillée » — couverture, puis titre et auteur
 * SOUS elle.
 *
 * Le texte est délibérément placé sous la jaquette et non en surimpression
 * (`BookCover(showTitle = true)`, chemin resté inutilisé) : superposer du
 * blanc sur une illustration arbitraire est illisible dès qu'elle est
 * claire, et recouvrir l'illustration va contre la raison même d'afficher
 * une couverture.
 *
 * La hauteur du bloc texte est **fixe** ([CAPTION_HEIGHT]) et non dictée par
 * le contenu : dans une `LazyVerticalGrid`, une rangée prend la hauteur de
 * sa cellule la plus haute — un titre sur deux lignes à côté d'un titre sur
 * une seule décalerait donc toute la rangée, et le pas vertical de la grille
 * changerait d'une rangée à l'autre.
 *
 * Deux zones cliquables distinctes (couverture, légende) plutôt qu'un
 * `clickable` sur la Column : [BookCover] pose déjà le sien sur la
 * couverture, l'imbriquer donnerait deux ondulations superposées pour un
 * seul tap.
 */
@Composable
private fun BookGridCell(
    publication: Publication,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    progressPercent: Int,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        BookCover(
            publication = publication,
            onClick = onClick,
            onToggleFavorite = onToggleFavorite,
            showTitle = false,
            progressPercent = progressPercent,
            onTogglePin = onTogglePin,
            onDelete = onDelete,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(CAPTION_HEIGHT)
                .clickable(onClick = onClick)
                .padding(top = 6.dp),
        ) {
            // Même nettoyage des artefacts EPUB (tiret, espaces en tête) que
            // la carte « Reprendre la lecture » — ces titres viennent des
            // mêmes métadonnées.
            Text(
                text = publication.title.cleanedForDisplay(),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (publication.authors.isNotEmpty()) {
                Text(
                    text = publication.authors.cleanedAuthorsForDisplay(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Hauteur fixe du bloc titre + auteur d'une cellule de « Grille détaillée » :
 * deux lignes de `bodySmall` et une de `labelSmall`, plus l'espace au-dessus.
 * Voir [BookGridCell] pour la raison du choix d'une hauteur fixe.
 */
private val CAPTION_HEIGHT = 58.dp

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
                    publication.title.cleanedForDisplay(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (publication.authors.isNotEmpty()) {
                    Text(
                        publication.authors.cleanedAuthorsForDisplay(),
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
            onToggleFavorite = onToggleFavorite,
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
private fun ResumeReadingCard(
    publication: Publication,
    progressPercent: Int,
    onClick: () -> Unit,
    isNarrating: Boolean,
    onTogglePlayback: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = InkToneShapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.Transparent
        )
    ) {
        val gradient = Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                MaterialTheme.colorScheme.surface
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = gradient)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Couverture miniature
            Box(
                modifier = Modifier
                    .size(width = 68.dp, height = 98.dp)
                    .shadow(4.dp, shape = InkToneShapes.medium)
                    .clip(InkToneShapes.medium)
            ) {
                BookCover(
                    publication = publication,
                    onClick = {},
                    onToggleFavorite = {},
                    showTitle = false,
                    showOverlays = false,
                    // Ce livre est AUSSI affiché dans la grille/liste en
                    // dessous : cette couverture-ci est la secondaire et ne
                    // doit pas revendiquer la clé `"cover-{id}"`, sinon
                    // l'emplacement de la grille reste vide (voir la note
                    // détaillée dans `BookCover`).
                    enableSharedTransition = false,
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Badge de statut
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = InkToneShapes.small,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "REPRENDRE LA LECTURE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Titre — nettoyage des artefacts EPUB (tiret, espaces en tête)
                Text(
                    text = publication.title.cleanedForDisplay(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Auteur — même nettoyage
                if (publication.authors.isNotEmpty()) {
                    Text(
                        text = publication.authors.cleanedAuthorsForDisplay(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Barre de progression
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$progressPercent%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Bouton Lecture/Pause — pilote la NARRATION seule, sans ouvrir
            // le Lecteur : écouter n'oblige plus à quitter la Bibliothèque.
            // L'ouverture du livre reste le tap sur la carte elle-même.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onTogglePlayback),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    symbol = if (isNarrating) AppSymbol.Pause else AppSymbol.Play,
                    contentDescription = if (isNarrating) {
                        "Mettre la narration en pause"
                    } else {
                        "Écouter la narration"
                    },
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
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
            Text("Import en cours · ${progress.current}/${progress.total}")
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
 * Illustration : [AppSymbol.LibraryShelf], variante au trait fin du glyphe
 * de l'entrée « Bibliothèque » du tiroir — l'écran vide et le chemin qui y
 * mène portent le même signe, dans le poids qui convient à chacun (seul et
 * grand ici, en ligne avec d'autres icônes là-bas). Remplace l'étagère à
 * emplacements pointillés dessinée au lot 10
 * (`EmptyLibraryShelfIllustration`), qui disait la même chose en un tracé
 * propre à ce seul écran.
 */
@Composable
private fun EmptyState(hasActiveImport: Boolean, onImportClick: () -> Unit, searchQuery: String = "") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Audit v1.0.0 (AUDIT_CONSOLIDATION_V1.md, M1) : une recherche
            // sans résultat n'est PAS une bibliothèque vide — avant le fix,
            // l'écran affichait « Votre bibliothèque est vide / Importez
            // votre premier livre » alors que la bibliothèque était pleine,
            // avec un CTA d'import trompeur.
            val isSearchNoResult = searchQuery.isNotBlank() && !hasActiveImport
            AppIcon(
                AppSymbol.LibraryShelf,
                contentDescription = null, // décoratif : le texte juste dessous porte le sens
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    hasActiveImport -> "Import en cours…"
                    isSearchNoResult -> "Aucun résultat"
                    else -> "Votre bibliothèque est vide"
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                when {
                    hasActiveImport -> "Vos livres apparaîtront ici une fois l'import terminé."
                    isSearchNoResult -> "Aucun livre ne correspond à « $searchQuery »."
                    else -> "Importez votre premier livre pour commencer à lire et écouter avec InkTone."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )
            if (!hasActiveImport && !isSearchNoResult) {
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
            AppIcon(
                AppSymbol.Error,
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

