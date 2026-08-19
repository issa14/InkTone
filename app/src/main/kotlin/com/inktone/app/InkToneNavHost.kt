package com.inktone.app

import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.inktone.core.designsystem.LocalAnimatedVisibilityScope
import com.inktone.core.designsystem.LocalSharedTransitionScope
import com.inktone.core.ui.AboutScreen
import com.inktone.feature.importer.ImportViewModel
import com.inktone.feature.library.DrawerDestination
import com.inktone.feature.library.LibraryDrawerContent
import com.inktone.feature.library.LibraryDetailCategory
import com.inktone.feature.library.LibraryDetailScreen
import com.inktone.feature.library.LibraryItemsScreen
import com.inktone.feature.library.LibraryScreen
import com.inktone.feature.library.RecentsScreen
import com.inktone.feature.onboarding.OnboardingScreen
import com.inktone.feature.opds.CatalogDashboardScreen
import com.inktone.feature.reader.ReaderIntent
import com.inktone.feature.reader.ReaderScreen
import com.inktone.feature.reader.ReaderViewModel
import com.inktone.feature.search.SearchScreen
import com.inktone.feature.settings.PronunciationRulesScreen
import com.inktone.feature.settings.SettingsScreen
import com.inktone.feature.settings.ThemeGalleryScreen
import com.inktone.feature.settings.ThemeStudioScreen
import com.inktone.feature.statistics.BookStatisticsScreen
import com.inktone.feature.statistics.StatisticsScreen
import com.inktone.feature.sync.SyncConfigurationScreen
import com.inktone.feature.sync.SyncConflictBottomSheet
import kotlinx.coroutines.launch

/**
 * Tâche 9bis.2 — `NavHost` réel, remplace `AppScreen` (état à 3 cas,
 * Phase 7).
 *
 * Lot 10 — [startDestination] devient un paramètre (`OnboardingRoute` au
 * premier lancement, `LibraryRoute` ensuite, arbitré par l'appelant sur
 * `UserPreferences.hasSeenOnboarding` — voir `MainActivity`) plutôt qu'un
 * `LibraryRoute` figé. Aucun `BackHandler` n'intercepte le retour système
 * depuis la carte 1 de l'onboarding : avec un seul back stack entry, le
 * retour système ferme l'app normalement plutôt que de laisser un écran
 * vide — comportement par défaut de Navigation Compose, pas un cas
 * particulier à coder.
 *
 * Retour prédictif Android : activé au niveau manifeste
 * (`android:enableOnBackInvokedCallback="true"`) — Navigation Compose
 * 2.8 anime déjà la transition de pop de back stack sur ce flag, aucun
 * `PredictiveBackHandler` par écran n'est nécessaire ici puisqu'aucun
 * écran ne consomme le geste retour lui-même (pas de swipe interne à
 * distinguer du retour système, contrairement à un pager ou un drawer).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun InkToneNavHost(navController: NavHostController = rememberNavController(), startDestination: Any = LibraryRoute) {
    // Lot 18 — un seul drawer, hoisté ici plutôt que local à
    // `LibraryScreen` : les 6 destinations principales le partagent et
    // l'ouvrent par leur hamburger, au lieu d'y accéder uniquement depuis
    // Bibliothèque avec une flèche de retour partout ailleurs.
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val drawerDestination = currentDestination?.toDrawerDestination()

    /**
     * Navigue vers une destination principale du drawer : `launchSingleTop`
     * + `popUpTo(startDestination)` gardent un back stack plat entre pairs
     * (aller de Récents à Statistiques n'empile pas Récents), et le retour
     * système revient toujours à la Bibliothèque plutôt que de dérouler
     * l'historique de navigation latérale.
     */
    fun navigateToDrawerDestination(route: Any) {
        scope.launch { drawerState.close() }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Le geste de balayage n'ouvre le drawer que sur les 6 destinations
        // principales : sur le Reader ou un écran de détail, il resterait en
        // conflit avec les gestes de l'écran (sélection de texte, retour).
        gesturesEnabled = drawerDestination != null || drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                LibraryDrawerContent(
                    selected = drawerDestination ?: DrawerDestination.LIBRARY,
                    onSelectLibrary = { navigateToDrawerDestination(LibraryRoute) },
                    onOpenRecents = { navigateToDrawerDestination(RecentsRoute) },
                    onOpenBookmarks = { navigateToDrawerDestination(BookmarksRoute) },
                    onOpenOpds = { navigateToDrawerDestination(OpdsRoute) },
                    onOpenSync = { navigateToDrawerDestination(SyncRoute) },
                    onOpenStats = { navigateToDrawerDestination(StatisticsRoute) },
                    // Pied de drawer — poussées classiques à flèche de
                    // retour, hors du jeu des destinations pairs (décision
                    // actée, plan du Lot 18) : pas de `popUpTo`.
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        navController.navigate(SettingsRoute)
                    },
                    onOpenThemes = {
                        scope.launch { drawerState.close() }
                        navController.navigate(ThemeGalleryRoute)
                    },
                    onOpenAbout = {
                        scope.launch { drawerState.close() }
                        navController.navigate(AboutRoute)
                    },
                )
            }
        },
    ) {
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
        NavHost(navController = navController, startDestination = startDestination) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onDone = {
                    // Retire l'onboarding du back stack : un retour système
                    // depuis la Bibliothèque ne doit jamais y ramener
                    // (indicateur déjà posé, piège explicite du plan).
                    navController.navigate(LibraryRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<LibraryRoute> {
            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
            val importViewModel: ImportViewModel = hiltViewModel()
            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenMultipleDocuments(),
            ) { uris ->
                if (uris.isNotEmpty()) {
                    importViewModel.enqueueImport(uris.map { it.toString() })
                }
            }
            LibraryScreen(
                onNavigateToReader = { publicationId -> navController.navigate(ReaderRoute(publicationId)) },
                // `LibraryEffect.NavigateToStats` (carte de statistiques de
                // la Bibliothèque) vise la même destination que l'item de
                // drawer : même navigation plate, pas une poussée.
                onOpenStats = { navigateToDrawerDestination(StatisticsRoute) },
                onMenuClick = openDrawer,
                onNavigateToSeriesDetail = { series -> navController.navigate(LibraryDetailRoute("series", series)) },
                onNavigateToTagDetail = { tag -> navController.navigate(LibraryDetailRoute("tag", tag)) },
                onImportClick = { importLauncher.launch(arrayOf("application/epub+zip", "text/plain", "application/pdf")) },
            )
            // Lot 11, tâche 11.10 — présenté à la prochaine ouverture de
            // l'app (Bibliothèque = première destination réelle après
            // l'onboarding) : un conflit détecté en arrière-plan ne se
            // résout jamais tout seul.
            SyncConflictBottomSheet()
            } // CompositionLocalProvider
        }
        composable<RecentsRoute> {
            RecentsScreen(
                onNavigateToReader = { publicationId -> navController.navigate(ReaderRoute(publicationId)) },
                onBack = navController::popBackStack,
            )
        }
        composable<ReaderRoute> { entry ->
            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
            val route = entry.toRoute<ReaderRoute>()
            val readerViewModel: ReaderViewModel = hiltViewModel()
            LaunchedEffect(route) {
                readerViewModel.onIntent(
                    ReaderIntent.OpenPublication(
                        publicationId = route.publicationId,
                        targetResourceHref = route.targetResourceHref,
                        targetChapterIndex = route.targetChapterIndex,
                        targetCharOffset = route.targetCharOffset,
                        flashOnArrival = route.flashOnArrival,
                    ),
                )
            }
            ReaderScreen(
                viewModel = readerViewModel,
                onSearchClick = { navController.navigate(SearchRoute) },
                onBack = { navController.popBackStack() },
                onOpenPronunciationRules = { navController.navigate(PronunciationRulesRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
            } // CompositionLocalProvider
        }
        composable<SearchRoute> {
            BackScaffold(title = "Rechercher", onBack = navController::popBackStack) {
                SearchScreen(
                    onNavigateToReader = { publicationId, resourceHref, chapterIndex, charOffset ->
                        navController.navigate(
                            ReaderRoute(
                                publicationId = publicationId,
                                targetResourceHref = resourceHref,
                                targetChapterIndex = chapterIndex,
                                targetCharOffset = charOffset,
                            ),
                        )
                    },
                )
            }
        }
        composable<SettingsRoute> {
            // Tache 9bis.5 : SettingsScreen possede desormais son propre
            // Scaffold/LargeTopAppBar (effet de collapse), pas de BackScaffold
            // generique ici contrairement aux autres destinations.
            //
            // Lot 6, Palier B — carte Données : BackupManager vit dans :data,
            // invisible depuis feature/settings (Blueprint §12.4). Ce
            // ViewModel et les deux lanceurs SAF (CreateDocument/OpenDocument,
            // meme pattern que ImportPickerButton) restent donc ici, dans app.
            val backupViewModel: BackupViewModel = hiltViewModel()
            val dataOperationResult by backupViewModel.lastResult.collectAsState()
            // Lot 11, tâche 11.1 — le fichier est désormais chiffré E2EE ;
            // un mot de passe est demandé après le choix de
            // l'emplacement/du fichier. Reste ici (tâche 11.6) : la carte
            // « Fichier local » de l'écran Configuration Sync pointe vers
            // cet écran plutôt que de dupliquer le mot de passe/l'export
            // /import une seconde fois — un seul chemin, pas deux qui
            // divergeraient.
            var pendingExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
            var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/octet-stream"),
            ) { uri -> pendingExportUri = uri }
            val importBackupLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> pendingImportUri = uri }

            SettingsScreen(
                onOpenAbout = { navController.navigate(AboutRoute) },
                onBack = navController::popBackStack,
                modelsFolderInfo = backupViewModel.modelsFolderInfo,
                dataOperationResult = dataOperationResult,
                onDismissDataOperationResult = backupViewModel::dismissResult,
                onExportData = {
                    exportLauncher.launch("inktone-backup-${java.time.LocalDate.now()}.rfbackup")
                },
                onImportData = {
                    importBackupLauncher.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
                },
            )

            pendingExportUri?.let { uri ->
                BackupPasswordDialog(
                    title = "Mot de passe de la sauvegarde",
                    confirmLabel = "Exporter",
                    showLossWarning = true,
                    onConfirm = { password ->
                        backupViewModel.exportTo(uri.toString(), password)
                        pendingExportUri = null
                    },
                    onDismiss = { pendingExportUri = null },
                )
            }
            pendingImportUri?.let { uri ->
                BackupPasswordDialog(
                    title = "Mot de passe (si la sauvegarde est chiffrée)",
                    confirmLabel = "Importer",
                    showLossWarning = false,
                    onConfirm = { password ->
                        backupViewModel.importFrom(uri.toString(), password.ifBlank { null })
                        pendingImportUri = null
                    },
                    onDismiss = { pendingImportUri = null },
                )
            }
        }
        composable<OpdsRoute> {
            CatalogDashboardScreen(
                onBack = navController::popBackStack,
                onOpenPublication = { publicationId -> navController.navigate(ReaderRoute(publicationId)) },
            )
        }
        composable<SyncRoute> {
            // Lot 11, tâche 11.6 — même pont que BackupViewModel : le
            // flux OAuth (Intent/ActivityResult) et l'écriture du compte
            // persisté sont hors de portée de feature/sync (jamais de
            // réseau/identifiants dans un module feature), donc pilotés
            // ici via SyncAuthViewModel (app -> infrastructure/sync + data).
            val syncAuthViewModel: SyncAuthViewModel = hiltViewModel()
            val isAuthenticating by syncAuthViewModel.isAuthenticating.collectAsState()
            val authError by syncAuthViewModel.authError.collectAsState()
            val authLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result -> syncAuthViewModel.onAuthorizationResult(result.data) }

            SyncConfigurationScreen(
                onBack = navController::popBackStack,
                onOpenLocalBackup = { navController.navigate(SettingsRoute) },
                isGoogleAuthenticating = isAuthenticating,
                isGoogleConfigured = syncAuthViewModel.isGoogleConfigured,
                onConnectGoogle = { authLauncher.launch(syncAuthViewModel.buildAuthorizationIntent()) },
                onDisconnectGoogle = syncAuthViewModel::disconnect,
            )

            authError?.let { message ->
                AlertDialog(
                    onDismissRequest = syncAuthViewModel::dismissError,
                    title = { Text("Échec de la connexion") },
                    text = { Text(message) },
                    confirmButton = { TextButton(onClick = syncAuthViewModel::dismissError) { Text("OK") } },
                )
            }
        }
        composable<PronunciationRulesRoute> {
            BackScaffold(title = "Regles de prononciation", onBack = navController::popBackStack) {
                PronunciationRulesScreen()
            }
        }
        composable<StatisticsRoute> {
            BackScaffold(title = "Statistiques", onBack = navController::popBackStack) {
                StatisticsScreen(
                    onNavigateToBookDetail = { bookId ->
                        navController.navigate(BookStatisticsRoute(bookId))
                    },
                )
            }
        }
        composable<BookStatisticsRoute> { entry ->
            val route = entry.toRoute<BookStatisticsRoute>()
            BookStatisticsScreen(
                onBack = navController::popBackStack,
                onSelectBook = { bookId -> navController.navigate(BookStatisticsRoute(bookId)) },
            )
        }
        composable<BookmarksRoute> {
            LibraryItemsScreen(
                onBack = navController::popBackStack,
                onNavigateToReader = { publicationId, resourceHref, chapterIndex, charOffset ->
                    navController.navigate(
                        ReaderRoute(
                            publicationId = publicationId,
                            targetResourceHref = resourceHref,
                            targetChapterIndex = chapterIndex,
                            targetCharOffset = charOffset,
                            // Lot 4, tâche 4.7 — flash différé du passage visé.
                            flashOnArrival = true,
                        ),
                    )
                },
            )
        }
        composable<AboutRoute> {
            BackScaffold(title = "A propos", onBack = navController::popBackStack) {
                AboutScreen(versionName = BuildConfig.VERSION_NAME)
            }
        }
        composable<ThemeGalleryRoute> {
            ThemeGalleryScreen(
                onBack = navController::popBackStack,
                onOpenStudio = { themeId -> navController.navigate(ThemeStudioRoute(themeId)) },
            )
        }
        composable<ThemeStudioRoute> { entry ->
            val route = entry.toRoute<ThemeStudioRoute>()
            ThemeStudioScreen(
                themeId = route.themeId,
                onSaved = navController::popBackStack,
                onDeleted = navController::popBackStack,
                onBack = navController::popBackStack,
            )
        }
        composable<LibraryDetailRoute> { entry ->
            val route = entry.toRoute<LibraryDetailRoute>()
            // Tache 2a.4 : LibraryDetailScreen possede son propre Scaffold
            // (titre a deux niveaux), pas de BackScaffold generique ici,
            // meme raison que SettingsScreen.
            LibraryDetailScreen(
                category = if (route.category == "series") LibraryDetailCategory.SERIES else LibraryDetailCategory.TAG,
                value = route.value,
                onNavigateToReader = { publicationId -> navController.navigate(ReaderRoute(publicationId)) },
                onBack = navController::popBackStack,
            )
        }
    }
        } // CompositionLocalProvider (SharedTransitionScope)
    } // SharedTransitionLayout
    } // ModalNavigationDrawer
}

/**
 * Lot 18 — associe la destination de navigation courante à l'item de
 * drawer à surligner. Une navigation profonde (`BookStatisticsRoute`
 * poussée depuis Statistiques) garde l'item de sa destination parente ;
 * un écran hors drawer (Reader, Réglages, pied de drawer) renvoie `null`
 * — aucun item surligné, et le geste de balayage désactivé.
 */
private fun NavDestination.toDrawerDestination(): DrawerDestination? = when {
    hasRoute<LibraryRoute>() -> DrawerDestination.LIBRARY
    hasRoute<RecentsRoute>() -> DrawerDestination.RECENTS
    hasRoute<BookmarksRoute>() -> DrawerDestination.BOOKMARKS
    hasRoute<OpdsRoute>() -> DrawerDestination.OPDS
    hasRoute<SyncRoute>() -> DrawerDestination.SYNC
    hasRoute<StatisticsRoute>() -> DrawerDestination.STATISTICS
    hasRoute<BookStatisticsRoute>() -> DrawerDestination.STATISTICS
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(AppSymbol.Back, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}
