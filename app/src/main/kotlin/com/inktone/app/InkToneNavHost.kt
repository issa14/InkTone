package com.inktone.app

import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.inktone.core.designsystem.LocalAnimatedVisibilityScope
import com.inktone.core.designsystem.LocalSharedTransitionScope
import com.inktone.core.designsystem.SystemBarIconsEffect
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
import com.inktone.feature.player.MiniPlayerBar
import com.inktone.feature.opds.CatalogDashboardScreen
import com.inktone.feature.reader.ReaderIntent
import com.inktone.feature.reader.ReaderScreen
import com.inktone.feature.reader.ReaderViewModel
import com.inktone.feature.search.SearchScreen
import com.inktone.feature.settings.AboutViewModel
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
     * + `popUpTo<LibraryRoute>` gardent un back stack plat entre pairs
     * (aller de Récents à Statistiques n'empile pas Récents), et le retour
     * système revient toujours à la Bibliothèque plutôt que de dérouler
     * l'historique de navigation latérale.
     *
     * L'ancre est `LibraryRoute` explicitement, pas
     * `graph.findStartDestination()` : au premier lancement la destination
     * de départ du graphe est `OnboardingRoute`, qui n'est plus dans le
     * back stack une fois l'onboarding terminé — `popUpTo` ne trouverait
     * rien à dépiler et la pile grossirait à chaque navigation latérale.
     */
    fun navigateToDrawerDestination(route: Any) {
        scope.launch { drawerState.close() }
        navController.navigate(route) {
            popUpTo<LibraryRoute> { saveState = true }
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
    // Contraste des icônes système — décidé ICI, en un seul point, à partir
    // de la destination ET de l'état du drawer.
    //
    // En edge-to-edge il n'y a plus de couleur de barre à poser : le contenu
    // peint derrière elle. Reste le contraste des icônes, qui dépend de ce
    // que ce contenu dessine — d'où le même calcul qu'avant, appliqué
    // désormais à `SystemBarIconsEffect`.
    //
    // Ce calcul a d'abord vécu dans chaque écran, au plus près de la barre du
    // haut qui le définit. Ce placement ne survit pas au drawer : à son
    // ouverture seul CE composable recompose (il relit `drawerState`), l'écran
    // non ; à sa fermeture, l'inverse — personne ne restaure. Deux écrivains
    // pour une même valeur, aucun ne voyant l'événement de l'autre.
    // Centraliser est la seule façon d'en faire une fonction de l'état plutôt
    // qu'une course. Le prix — une table à tenir en phase avec la couleur
    // réelle des barres du haut — est payé par `usesPrimaryTopBar()`, même
    // idiome que `hidesMiniPlayer()`/`toDrawerDestination()` juste à côté.
    //
    // Drawer ouvert : c'est sa feuille, en `surface`, qui occupe le haut de
    // l'écran — les icônes doivent contraster avec elle, pas avec la barre du
    // haut qu'elle recouvre.
    val systemBarBackground = when {
        drawerState.isOpen -> MaterialTheme.colorScheme.surface
        currentDestination?.usesPrimaryTopBar() == true -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    SystemBarIconsEffect(systemBarBackground)
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
        // P2 — le mini-lecteur occupe une bande sous le contenu plutôt que de
        // le recouvrir : une barre flottante masquerait les actions ancrées en
        // bas des écrans (import, filtres de bibliothèque).
        Column(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.weight(1f),
        ) {
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
                onNavigateToReader = { publicationId, autoStartTts ->
                    navController.navigate(ReaderRoute(publicationId, autoStartTts = autoStartTts))
                },
                // `LibraryEffect.NavigateToStats` (carte de statistiques de
                // la Bibliothèque) vise la même destination que l'item de
                // drawer : même navigation plate, pas une poussée.
                onOpenStats = { navigateToDrawerDestination(StatisticsRoute) },
                onMenuClick = openDrawer,
                onNavigateToSeriesDetail = { series -> navController.navigate(LibraryDetailRoute("series", series)) },
                onNavigateToTagDetail = { tag -> navController.navigate(LibraryDetailRoute("tag", tag)) },
                onImportClick = { importLauncher.launch(arrayOf("application/epub+zip", "text/plain", "application/pdf")) },
                // Lot 19 — « Synchroniser avec le cloud » non configuré
                // bascule vers l'écran de configuration de la sync.
                onOpenSync = { navigateToDrawerDestination(SyncRoute) },
            )
            // Lot 11, tâche 11.10 — présenté à la prochaine ouverture de
            // l'app (Bibliothèque = première destination réelle après
            // l'onboarding) : un conflit détecté en arrière-plan ne se
            // résout jamais tout seul.
            SyncConflictBottomSheet()
            } // CompositionLocalProvider
        }
        composable<RecentsRoute> {
            // Diagnostic flash de fermeture — bug réel trouvé à l'audit :
            // sans ce scope, `ReaderScreen` ne trouve pas de
            // `LocalAnimatedVisibilityScope` (runCatching retombe sur
            // null) et désactive silencieusement la transition partagée
            // vers la couverture. À la fermeture, plus de morphing vers
            // une petite couverture : Compose Navigation retombe sur son
            // fondu par défaut du Lecteur PLEIN ÉCRAN (fond coloré du
            // thème + texte du chapitre), visible ~300 ms — la « page
            // colorée avec texte » signalée. `RecentsScreen` affiche des
            // `BookCover`/`PublicationListRow` porteurs de la même clé
            // `"cover-$id"` (voir `BookCover.kt`) : leur fournir ce scope
            // suffit à réactiver le morphing, même correctif que
            // `LibraryRoute`/`ReaderRoute` ci-dessus.
            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
            RecentsScreen(
                onNavigateToReader = { publicationId -> navController.navigate(ReaderRoute(publicationId)) },
                onMenuClick = openDrawer,
            )
            } // CompositionLocalProvider
        }
        composable<ReaderRoute>(
            // Diagnostic flash de fermeture — filet de sécurité pour les
            // cas où la transition partagée vers la couverture ne trouve
            // aucune correspondance (Recherche sans couverture, item
            // scrollé hors écran, livre réordonné entre-temps par
            // `setLastOpened`) : par défaut, Compose Navigation applique
            // alors son propre fondu (~300 ms) au Lecteur PLEIN ÉCRAN
            // (fond coloré du thème + texte du chapitre), visible le
            // temps du fondu — la « page colorée » signalée. Un fondu de
            // fermeture volontairement court rend ce repli imperceptible
            // sans toucher au cas nominal (morphing shared element),
            // prioritaire sur cette transition par défaut quand il existe.
            exitTransition = { fadeOut(animationSpec = tween(READER_CLOSE_FADE_MS)) },
            popExitTransition = { fadeOut(animationSpec = tween(READER_CLOSE_FADE_MS)) },
        ) { entry ->
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
                        autoStartTts = route.autoStartTts,
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
                onMenuClick = openDrawer,
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
            val isWebDavConnecting by syncAuthViewModel.isWebDavConnecting.collectAsState()
            val webDavError by syncAuthViewModel.webDavError.collectAsState()
            val authLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result -> syncAuthViewModel.onAuthorizationResult(result.data) }

            SyncConfigurationScreen(
                onBack = navController::popBackStack,
                onMenuClick = openDrawer,
                onOpenLocalBackup = { navController.navigate(SettingsRoute) },
                isGoogleAuthenticating = isAuthenticating,
                isGoogleConfigured = syncAuthViewModel.isGoogleConfigured,
                onConnectGoogle = { authLauncher.launch(syncAuthViewModel.buildAuthorizationIntent()) },
                onDisconnectGoogle = syncAuthViewModel::disconnect,
                isWebDavConnecting = isWebDavConnecting,
                onConnectWebDav = syncAuthViewModel::connectWebDav,
                onDisconnectWebDav = syncAuthViewModel::disconnectWebDav,
            )

            authError?.let { message ->
                AlertDialog(
                    onDismissRequest = syncAuthViewModel::dismissError,
                    title = { Text("Échec de la connexion") },
                    text = { Text(message) },
                    confirmButton = { TextButton(onClick = syncAuthViewModel::dismissError) { Text("OK") } },
                )
            }
            webDavError?.let { message ->
                AlertDialog(
                    onDismissRequest = syncAuthViewModel::dismissWebDavError,
                    title = { Text("Échec de la connexion WebDAV") },
                    text = { Text(message) },
                    confirmButton = { TextButton(onClick = syncAuthViewModel::dismissWebDavError) { Text("OK") } },
                )
            }
        }
        composable<PronunciationRulesRoute> {
            BackScaffold(title = "Regles de prononciation", onBack = navController::popBackStack) {
                PronunciationRulesScreen()
            }
        }
        composable<StatisticsRoute> {
            StatisticsScreen(
                onNavigateToBookDetail = { bookId ->
                    navController.navigate(BookStatisticsRoute(bookId))
                },
                onMenuClick = openDrawer,
            )
        }
        composable<BookStatisticsRoute> { entry ->
            val route = entry.toRoute<BookStatisticsRoute>()
            BookStatisticsScreen(
                onBack = navController::popBackStack,
                onSelectBook = { bookId -> navController.navigate(BookStatisticsRoute(bookId)) },
            )
        }
        composable<BookmarksRoute> {
            // Diagnostic flash de fermeture — même correctif que
            // `RecentsRoute` ci-dessus : sans ce scope, la transition
            // partagée vers la couverture est désactivée et la fermeture
            // laisse voir le fondu par défaut du Lecteur plein écran.
            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
            LibraryItemsScreen(
                onMenuClick = openDrawer,
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
            } // CompositionLocalProvider
        }
        composable<AboutRoute> {
            val aboutViewModel: AboutViewModel = hiltViewModel()
            val ttsEngineLabel by aboutViewModel.ttsEngineLabel.collectAsState()
            BackScaffold(title = "A propos", onBack = navController::popBackStack) {
                AboutScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    ttsEngineLabel = ttsEngineLabel,
                )
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
            //
            // Diagnostic flash de fermeture — même correctif que
            // `RecentsRoute`/`BookmarksRoute` ci-dessus.
            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
            LibraryDetailScreen(
                category = if (route.category == "series") LibraryDetailCategory.SERIES else LibraryDetailCategory.TAG,
                value = route.value,
                onNavigateToReader = { publicationId -> navController.navigate(ReaderRoute(publicationId)) },
                onBack = navController::popBackStack,
            )
            } // CompositionLocalProvider
        }
    }
        // Masqué sur le Lecteur (qui a ses propres commandes TTS) et sur
        // l'onboarding (aucune narration possible avant le premier import).
        if (currentDestination?.hidesMiniPlayer() != true) {
            MiniPlayerBar(
                onOpenReader = { publicationId ->
                    navController.navigate(ReaderRoute(publicationId)) { launchSingleTop = true }
                },
                // La Bibliothèque est le seul écran à porter la carte
                // « Reprendre la lecture » : c'est le seul où cette barre peut
                // faire doublon (voir MiniPlayerUiState.isRedundantWithResumeCard).
                isResumeCardVisible = currentDestination?.hasRoute<LibraryRoute>() == true,
            )
        }
        } // Column (contenu + mini-lecteur)
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
/**
 * P2 — écrans où le mini-lecteur ne doit pas apparaître : le Lecteur, qui a
 * ses propres commandes TTS (une seconde barre ferait doublon et volerait de
 * la hauteur de page), et l'onboarding, où aucune narration ne peut exister.
 */
/**
 * Destinations dont la `TopAppBar` est peinte en `colorScheme.primary`, et
 * dont la barre de statut doit donc prendre la même couleur.
 *
 * À tenir en phase avec le `containerColor` réel de ces écrans — c'est le
 * prix de la centralisation (voir le commentaire de `InkToneNavHost`). Les
 * autres destinations gardent `surface`, la couleur de leur `BackScaffold`
 * ou de leur propre barre. `ReaderRoute` en fait partie et n'a rien à y
 * gagner : il masque les barres système et peint lui-même son fond de
 * fenêtre (`WindowBackgroundColorEffect`).
 */
private fun NavDestination.usesPrimaryTopBar(): Boolean =
    hasRoute<LibraryRoute>() ||
        hasRoute<RecentsRoute>() ||
        hasRoute<BookmarksRoute>() ||
        hasRoute<LibraryDetailRoute>() ||
        hasRoute<StatisticsRoute>() ||
        hasRoute<BookStatisticsRoute>() ||
        hasRoute<SyncRoute>() ||
        hasRoute<OpdsRoute>()

private fun NavDestination.hidesMiniPlayer(): Boolean =
    hasRoute<ReaderRoute>() || hasRoute<OnboardingRoute>()

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

/**
 * Durée du fondu de fermeture du Lecteur (voir `composable<ReaderRoute>`) —
 * volontairement courte, repli pour les cas sans transition partagée
 * (shared element) plutôt qu'une animation soignée en soi.
 */
private const val READER_CLOSE_FADE_MS = 120

