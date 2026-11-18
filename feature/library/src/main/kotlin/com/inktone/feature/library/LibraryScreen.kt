package com.inktone.feature.library

import com.inktone.core.designsystem.InkToneSpacing

import com.inktone.core.designsystem.InkToneTextField
import com.inktone.core.designsystem.emphasized
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
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.NarrationPlayButton
import com.inktone.core.designsystem.AppSymbol
import com.inktone.core.designsystem.InkToneBrandMark
import com.inktone.core.designsystem.NarrativeAccentFamily
import com.inktone.core.designsystem.InkToneShapes
import com.inktone.core.designsystem.formatPercent
import com.inktone.core.designsystem.InkToneTopBar
import com.inktone.core.designsystem.InkToneEmptyState
import com.inktone.core.designsystem.InkToneErrorState
import com.inktone.domain.model.FilterMode
import com.inktone.domain.model.Publication
import com.inktone.domain.model.cleanedAuthorsForDisplay
import com.inktone.domain.model.cleanedForDisplay
import com.inktone.domain.service.ImportProgress
import com.inktone.domain.service.SyncFailureReason
import com.inktone.domain.service.SyncOperationResult
import kotlinx.coroutines.launch

/**
 * Tache 9bis.4 — reconstruction complete : drawer (filtres/series/tags),
 * recherche titre/auteur integree, tri, bascule grille/liste, carte
 * "reprendre la lecture" proeminente, favoris, chargement shimmer.
 * Remplace la grille nue de la Tache 6.6.
 *
 * `onNavigateToReader` : câblé par l'appelant (`InkToneNavHost`, Tâche
 * 9bis.2).
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
    // Audit UX A3 — recherche dans le texte de tous les livres.
    onOpenTextSearch: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                is LibraryEffect.NavigateToSync -> onOpenSync()
                is LibraryEffect.RandomBookUnavailable -> scope.launch {
                    snackbarHostState.showSnackbar("Aucun livre à ouvrir")
                }
                // Audit UX B15 : rien n'indiquait qu'une synchronisation était en cours,
                // et l'échec s'affichait sans motif.
                is LibraryEffect.SyncStarted -> scope.launch {
                    snackbarHostState.showSnackbar("Synchronisation en cours…", duration = SnackbarDuration.Indefinite)
                }
                is LibraryEffect.SyncCompleted -> scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(syncResultMessage(effect.result))
                }
            }
        }
    }

    // Le retour système ferme la recherche au lieu de quitter l'application.
    BackHandler(enabled = state.isSearchActive) {
        viewModel.onIntent(LibraryIntent.SetSearchActive(false))
    }

    // Importer est l'action principale de l'écran (audit UI P1-11) : bouton
    // flottant dès que la bibliothèque montre des livres. Vide, l'état vide
    // porte déjà « Importer votre premier livre ».
    val showImportFab = !state.isLoading && state.errorMessage == null && !state.isSearchActive &&
        !state.showImportDetails && state.displayedPublications.isNotEmpty()
    // Replié en icône seule pendant un défilement vers le bas, où le libellé
    // masquerait des couvertures ; déplié à la remontée.
    var isImportFabExpanded by remember { mutableStateOf(true) }
    val fabScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -1f) isImportFabExpanded = false
                if (available.y > 1f) isImportFabExpanded = true
                return Offset.Zero
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (showImportFab) {
                ExtendedFloatingActionButton(
                    text = { Text("Importer") },
                    icon = { AppIcon(AppSymbol.Add, contentDescription = null) },
                    onClick = onImportClick,
                    expanded = isImportFabExpanded,
                )
            }
        },
        topBar = {
            LibraryTopBar(
                searchQuery = state.searchQuery,
                onSearchQueryChange = { viewModel.onIntent(LibraryIntent.SetSearchQuery(it)) },
                isSearchActive = state.isSearchActive,
                onSearchActiveChange = { viewModel.onIntent(LibraryIntent.SetSearchActive(it)) },
                sortOrder = state.sortOrder,
                onSortOrderChange = { viewModel.onIntent(LibraryIntent.SetSortOrder(it)) },
                layoutMode = state.layoutMode,
                onLayoutModeChange = { viewModel.onIntent(LibraryIntent.SetLayoutMode(it)) },
                selectedFormats = state.selectedFormats,
                onToggleFormat = { viewModel.onIntent(LibraryIntent.ToggleFileFormat(it)) },
                onClearFormats = { viewModel.onIntent(LibraryIntent.ClearFileFormats) },
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
                onOpenTextSearch = onOpenTextSearch,
                onSyncNow = { viewModel.onIntent(LibraryIntent.SyncNow) },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).nestedScroll(fabScrollConnection)) {
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
            }
            // Affichée même quand le résumé d'un import précédent n'a pas été
            // fermé : un nouvel import lancé entre-temps progressait sans
            // aucun signe à l'écran. Se masque d'elle-même hors import.
            ImportProgressBanner(state.importProgress)

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
                    hasActiveImport = state.importProgress.isActive,
                    onImportClick = onImportClick,
                    searchQuery = state.searchQuery,
                    isFiltered = state.activeFilter != FilterMode.ALL || state.selectedFormats.isNotEmpty(),
                    onShowAllBooks = {
                        viewModel.onIntent(LibraryIntent.ChangeFilter(FilterMode.ALL))
                        viewModel.onIntent(LibraryIntent.ClearFileFormats)
                    },
                )
                else -> {
                    // Carte « Reprendre la lecture » fixe au-dessus de la liste :
                    // reste visible en permanence, seule la bibliothèque défile
                    // dessous (au lieu d'un premier item qui disparaissait au scroll).
                    state.resumeReadingPublication?.let { resume ->
                        ResumeReadingCard(
                            publication = resume,
                            progressPercent = state.progressMap[resume.id], // inconnue : aucun chiffre plutôt que « 0% » (audit UX A19)
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
                .padding(start = InkToneSpacing.xl, end = InkToneSpacing.xl, top = 28.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkToneBrandMark(modifier = Modifier.size(44.dp))
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    "InkTone",
                    style = MaterialTheme.typography.headlineSmall.emphasized(),
                    fontFamily = NarrativeAccentFamily,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // Symétrique du séparateur qui coiffe déjà le pied de drawer : sans
        // elle, la liste de navigation est bornée en bas et pas en haut.
        HorizontalDivider()
        Column(Modifier.weight(1f).padding(InkToneSpacing.lg)) {
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
            label = { Text("Signets et notes") },
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
            label = { Text("Catalogues en ligne") },
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
            label = { Text("Statistiques") },
            icon = { AppIcon(AppSymbol.Stats,  contentDescription = null) },
            selected = selected == DrawerDestination.STATISTICS,
            onClick = onOpenStats,
        )

        // ──── #2 Footer drawer ────
        Spacer(Modifier.weight(1f))
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = InkToneSpacing.md),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DrawerFooterItem("Réglages", AppSymbol.Settings) { onOpenSettings() }
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
        modifier = Modifier.clickable(onClick = onClick).padding(InkToneSpacing.sm),
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
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    sortOrder: LibrarySortOrder,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
    layoutMode: LibraryLayoutMode,
    onLayoutModeChange: (LibraryLayoutMode) -> Unit,
    selectedFormats: Set<com.inktone.domain.model.PublicationFormat>,
    onToggleFormat: (com.inktone.domain.model.PublicationFormat) -> Unit,
    onClearFormats: () -> Unit,
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
    onOpenTextSearch: () -> Unit = {},
    onSyncNow: () -> Unit = {},
) {
    var showFilterDialog by remember { mutableStateOf(false) }
    var showActionsSheet by remember { mutableStateOf(false) }
    var showNavPopup by remember { mutableStateOf(false) }

    if (isSearchActive) {
        // Clavier ouvert à l'ouverture de la recherche, pas quand la barre
        // réapparaît avec une requête déjà tapée (retour du Lecteur) : le
        // clavier recouvrirait les résultats.
        val searchFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            if (searchQuery.isEmpty()) searchFocusRequester.requestFocus()
        }
        // État recherche : SearchBar pleine largeur qui remplace la TopBar
        TopAppBar(
            title = {
                TextField(
                    shape = InkToneTextField.searchShape,
                    colors = InkToneTextField.searchColors(),
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                    placeholder = { Text("Titre, auteur, série, étiquette…") },
                    singleLine = true,
                )
            },
            navigationIcon = {
                IconButton(onClick = { onSearchActiveChange(false) }) {
                    AppIcon(AppSymbol.Back, contentDescription = "Fermer")
                }
            },
            colors = InkToneTopBar.colors(),
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
                            color = InkToneTopBar.contentColor(),
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
                            tint = InkToneTopBar.contentColor(),
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
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    AppIcon(AppSymbol.Search, contentDescription = "Rechercher")
                }
                IconButton(onClick = { showFilterDialog = true }) {
                    AppIcon(AppSymbol.Filter,  contentDescription = "Filtrer")
                }
                IconButton(onClick = { showActionsSheet = true }) {
                    AppIcon(AppSymbol.MoreActions, contentDescription = "Actions")
                }
            },
            colors = InkToneTopBar.colors(),
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
            Column(Modifier.padding(bottom = InkToneSpacing.xxl)) {
                Text("Bibliothèque", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = InkToneSpacing.xl, vertical = InkToneSpacing.sm))
                ActionSheetItem("Rechercher dans les livres", AppSymbol.Search) { showActionsSheet = false; onOpenTextSearch() }
                ActionSheetItem("Ouvrir un livre au hasard", AppSymbol.Reading) { showActionsSheet = false; onOpenRandomBook() }
                ActionSheetItem("Synchroniser avec le cloud", AppSymbol.Sync) { showActionsSheet = false; onSyncNow() }
            }
        }
    }
}

@Composable
private fun ActionSheetItem(label: String, icon: AppSymbol, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = InkToneSpacing.xl, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(InkToneSpacing.lg))
        Text(label, style = MaterialTheme.typography.bodyLarge)
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

/** Marge basse : le dernier rang défile au-dessus du bouton flottant Importer. */
private val LibraryListPadding = PaddingValues(start = InkToneSpacing.sm, top = InkToneSpacing.sm, end = InkToneSpacing.sm, bottom = 88.dp)

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
                contentPadding = LibraryListPadding,
            ) {
                gridItems(state.displayedPublications, key = { it.id }) { publication ->
                    BookCover(
                        publication = publication,
                        onClick = { onOpen(publication.id) },
                        onToggleFavorite = { onToggleFavorite(publication.id, !publication.isFavorite) },
                        modifier = Modifier.padding(InkToneSpacing.sm),
                        showTitle = false,
                        progressPercent = state.progressMap[publication.id] ?: 0,
                        isStarted = publication.id in state.startedPublicationIds,
                        onTogglePin = { onTogglePin(publication.id, !publication.isPinned) },
                        onDelete = { onDelete(publication.id) },
                    )
                }
            }
        }

        LibraryLayoutMode.GRID_DETAILED -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = LibraryListPadding,
            ) {
                gridItems(state.displayedPublications, key = { it.id }) { publication ->
                    BookGridCell(
                        publication = publication,
                        onClick = { onOpen(publication.id) },
                        onToggleFavorite = { onToggleFavorite(publication.id, !publication.isFavorite) },
                        progressPercent = state.progressMap[publication.id] ?: 0,
                        isStarted = publication.id in state.startedPublicationIds,
                        onTogglePin = { onTogglePin(publication.id, !publication.isPinned) },
                        onDelete = { onDelete(publication.id) },
                        modifier = Modifier.padding(InkToneSpacing.sm),
                    )
                }
            }
        }

        LibraryLayoutMode.LIST -> {
            LazyColumn(contentPadding = LibraryListPadding) {
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
 * La hauteur du bloc texte est **constante** et non dictée par le contenu :
 * dans une `LazyVerticalGrid`, une rangée prend la hauteur de sa cellule la
 * plus haute — un titre sur une ligne à côté d'un titre sur deux décalerait
 * toute la rangée. Elle est obtenue par `minLines` (titre sur deux lignes,
 * auteur toujours présent) et non par une hauteur en dp : celle-ci rognait
 * l'auteur dès que l'échelle de police dépassait 1.
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
    isStarted: Boolean,
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
            isStarted = isStarted,
            onTogglePin = onTogglePin,
            onDelete = onDelete,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(top = 6.dp),
        ) {
            // Même nettoyage des artefacts EPUB (tiret, espaces en tête) que
            // la carte « Reprendre la lecture » — ces titres viennent des
            // mêmes métadonnées.
            Text(
                text = publication.title.cleanedForDisplay(),
                style = MaterialTheme.typography.titleSmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Toujours composé, même vide : sinon une cellule sans auteur
            // serait plus basse que ses voisines.
            Text(
                text = if (publication.authors.isNotEmpty()) publication.authors.cleanedAuthorsForDisplay() else "",
                style = MaterialTheme.typography.labelSmall,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            .padding(horizontal = InkToneSpacing.md, vertical = 9.dp),
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
                    onClick = null,
                    onToggleFavorite = {},
                    showTitle = false,
                    showOverlays = false,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = InkToneSpacing.md),
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
                modifier = Modifier.fillMaxWidth().padding(top = InkToneSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(
                    progress = { progressPercent / 100f },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatPercent(progressPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = InkToneSpacing.sm),
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
    progressPercent: Int?,
    onClick: () -> Unit,
    isNarrating: Boolean,
    onTogglePlayback: () -> Unit,
) {
    // Surface pleine sans élévation : sur un fond transparent, l'ombre d'une
    // ElevatedCard transparaissait à travers le dégradé (audit UI P2-5).
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(InkToneSpacing.sm),
        shape = InkToneShapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(InkToneSpacing.lg),
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
                    onClick = null,
                    onToggleFavorite = {},
                    progressPercent = progressPercent ?: 0,
                    isStarted = true,
                    showTitle = false,
                    showOverlays = false,
                )
            }

            Spacer(Modifier.width(InkToneSpacing.lg))

            Column(modifier = Modifier.weight(1f)) {
                // Simple libellé : une pastille pesait plus que le titre du livre.
                Text(
                    text = "Reprendre",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = InkToneSpacing.xs),
                )

                // Titre — nettoyage des artefacts EPUB (tiret, espaces en tête)
                Text(
                    text = publication.title.cleanedForDisplay(),
                    style = MaterialTheme.typography.titleMedium,
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
                        modifier = Modifier.padding(bottom = InkToneSpacing.sm)
                    )
                }

                // Barre de progression — absente si la progression est inconnue
                if (progressPercent != null) Row(
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
                    Spacer(Modifier.width(InkToneSpacing.sm))
                    Text(
                        text = formatPercent(progressPercent),
                        style = MaterialTheme.typography.labelSmall.emphasized(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.width(InkToneSpacing.sm))

            // Bouton Lecture/Pause — pilote la NARRATION seule, sans ouvrir
            // le Lecteur : écouter n'oblige plus à quitter la Bibliothèque.
            // L'ouverture du livre reste le tap sur la carte elle-même.
            NarrationPlayButton(
                isPlaying = isNarrating,
                onClick = onTogglePlayback,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 36.dp,
                shape = CircleShape,
            )
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
    if (!progress.isActive) return

    Column(Modifier.fillMaxWidth().padding(horizontal = InkToneSpacing.sm, vertical = InkToneSpacing.xs)) {
        if (progress.total > 0) {
            LinearProgressIndicator(
                progress = { progress.current.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Import en cours · ${progress.current}/${progress.total}")
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(if (progress.isRunning) "Import en cours…" else "Import en attente…")
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
private fun EmptyState(
    hasActiveImport: Boolean,
    onImportClick: () -> Unit,
    searchQuery: String = "",
    isFiltered: Boolean = false,
    onShowAllBooks: () -> Unit = {},
) {
    // Une recherche ou un filtre sans résultat n'est pas une bibliothèque
    // vide : ni le même titre, ni l'invitation à importer.
    val isSearchNoResult = searchQuery.isNotBlank() && !hasActiveImport
    val isFilterNoResult = isFiltered && !hasActiveImport && !isSearchNoResult
    InkToneEmptyState(
        icon = AppSymbol.LibraryShelf,
        title = when {
            hasActiveImport -> "Import en cours…"
            isSearchNoResult -> "Aucun résultat"
            isFilterNoResult -> "Aucun livre dans ce filtre"
            else -> "Votre bibliothèque est vide"
        },
        message = when {
            hasActiveImport -> "Vos livres apparaîtront ici une fois l'import terminé."
            isSearchNoResult -> "Aucun livre ne correspond à « $searchQuery »."
            isFilterNoResult -> null
            else -> "EPUB, PDF ou TXT, depuis votre téléphone."
        },
        action = when {
            isFilterNoResult -> { { Button(onClick = onShowAllBooks) { Text("Afficher tous les livres") } } }
            !hasActiveImport && !isSearchNoResult -> { { Button(onClick = onImportClick) { Text("Importer votre premier livre") } } }
            else -> null
        },
    )
}

/**
 * Bannière d'erreur avec message et bouton « Réessayer ».
 * Affichée quand [LibraryUiState.errorMessage] est non-null.
 */
@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    InkToneErrorState(
        message = message,
        onRetry = onRetry,
        secondaryActionLabel = "Ignorer",
        onSecondaryAction = onDismiss,
    )
}

/**
 * Message du résultat d'une synchronisation manuelle. Le motif vient du type
 * d'échec, jamais du texte technique du fournisseur (audit UI P1-13).
 */
internal fun syncResultMessage(result: SyncOperationResult): String = when (result) {
    is SyncOperationResult.Success -> "Synchronisation terminée"
    is SyncOperationResult.Failed -> when (result.reason) {
        SyncFailureReason.INVALID_TOKEN -> "Échec de la synchronisation : reconnectez votre compte."
        SyncFailureReason.QUOTA_EXCEEDED -> "Échec de la synchronisation : l'espace du compte est plein."
        SyncFailureReason.NETWORK -> "Échec de la synchronisation : serveur injoignable."
        SyncFailureReason.NOT_FOUND -> "Échec de la synchronisation : données introuvables sur le serveur."
        SyncFailureReason.UNKNOWN -> "Échec de la synchronisation"
    }
}
