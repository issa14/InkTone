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
import androidx.compose.runtime.remember
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
import com.inktone.feature.library.GlobalBookmarksScreen
import com.inktone.feature.library.LibraryScreen
import com.inktone.feature.reader.ReaderIntent
import com.inktone.feature.reader.ReaderScreen
import com.inktone.feature.reader.ReaderViewModel
import com.inktone.feature.search.SearchScreen
import com.inktone.feature.settings.PronunciationRulesScreen
import com.inktone.feature.settings.SettingsScreen
import com.inktone.feature.statistics.StatisticsScreen

/**
 * Tâche 9bis.2 — `NavHost` réel, remplace `AppScreen` (état à 3 cas,
 * Phase 7). `LibraryRoute` reste l'écran de démarrage (inchangé).
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
fun InkToneNavHost(navController: NavHostController = rememberNavController()) {
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
        NavHost(navController = navController, startDestination = LibraryRoute) {
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
                onOpenBookmarks = { navController.navigate(BookmarksRoute) },
                onOpenStats = { navController.navigate(StatisticsRoute) },
                onImportClick = { importLauncher.launch(arrayOf("application/epub+zip", "text/plain")) },
                // Tache 1.0 (Partie 1) : seul l'import reste dans le FAB.
                // Search/Stats/Settings sont atteignables uniquement depuis
                // le drawer (Partie 2) — c'est tout l'interet d'avoir un
                // drawer plutot que des icones eparpillees.
                floatingActionButton = { ImportPickerButton() },
            )
            } // CompositionLocalProvider
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
                    ),
                )
            }
            ReaderScreen(viewModel = readerViewModel, onSearchClick = { navController.navigate(SearchRoute) })
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
            SettingsScreen(
                onOpenPronunciationRules = { navController.navigate(PronunciationRulesRoute) },
                onOpenAbout = { navController.navigate(AboutRoute) },
                onBack = navController::popBackStack,
            )
        }
        composable<PronunciationRulesRoute> {
            BackScaffold(title = "Regles de prononciation", onBack = navController::popBackStack) {
                PronunciationRulesScreen()
            }
        }
        composable<StatisticsRoute> {
            BackScaffold(title = "Statistiques", onBack = navController::popBackStack) {
                StatisticsScreen()
            }
        }
        composable<BookmarksRoute> {
            BackScaffold(title = "Signets", onBack = navController::popBackStack) {
                GlobalBookmarksScreen(
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
        composable<AboutRoute> {
            BackScaffold(title = "A propos", onBack = navController::popBackStack) {
                AboutScreen()
            }
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
