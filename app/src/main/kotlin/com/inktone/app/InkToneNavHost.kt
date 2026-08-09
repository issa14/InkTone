package com.inktone.app

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.inktone.core.designsystem.LocalAnimatedVisibilityScope
import com.inktone.core.designsystem.LocalSharedTransitionScope
import com.inktone.core.ui.AboutScreen
import com.inktone.feature.importer.ImportPickerButton
import com.inktone.feature.importer.ImportViewModel
import com.inktone.feature.library.LibraryDetailCategory
import com.inktone.feature.library.LibraryDetailScreen
import com.inktone.feature.library.LibraryItemsScreen
import com.inktone.feature.library.LibraryScreen
import com.inktone.feature.library.RecentsScreen
import com.inktone.feature.onboarding.OnboardingScreen
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
                onOpenRecents = { navController.navigate(RecentsRoute) },
                onOpenBookmarks = { navController.navigate(BookmarksRoute) },
                onOpenStats = { navController.navigate(StatisticsRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenAbout = { navController.navigate(AboutRoute) },
                onOpenThemes = { navController.navigate(ThemeGalleryRoute) },
                onNavigateToSeriesDetail = { series -> navController.navigate(LibraryDetailRoute("series", series)) },
                onNavigateToTagDetail = { tag -> navController.navigate(LibraryDetailRoute("tag", tag)) },
                onImportClick = { importLauncher.launch(arrayOf("application/epub+zip", "text/plain")) },
                // Tache 1.0 (Partie 1) : seul l'import reste dans le FAB.
                // Search/Stats/Settings sont atteignables uniquement depuis
                // le drawer (Partie 2) — c'est tout l'interet d'avoir un
                // drawer plutot que des icones eparpillees.
                floatingActionButton = { ImportPickerButton() },
            )
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
            // l'emplacement/du fichier. Invite provisoire
            // (BackupPasswordDialog), remplacée par la carte « Fichier
            // local » dédiée à la tâche 11.6.
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
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
