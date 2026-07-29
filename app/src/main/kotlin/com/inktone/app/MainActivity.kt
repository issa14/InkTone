package com.inktone.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.feature.importer.ImportPickerButton
import com.inktone.feature.library.LibraryScreen
import com.inktone.feature.reader.ReaderIntent
import com.inktone.feature.reader.ReaderScreen
import com.inktone.feature.reader.ReaderViewModel
import com.inktone.feature.search.SearchScreen
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

/**
 * Point d'entrée de l'app (Tâche 7.1bis, révision) — jusqu'ici (Phases 3
 * à 7) hébergeait `ReaderScreen` directement, sans navigation réelle.
 * `LibraryScreen` (Tâche 6.6) existait mais n'était appelé nulle part.
 * Écran par défaut maintenant `LibraryScreen` (avec `ImportPickerButton`
 * en FAB, câblé depuis la Tâche 6.2bis), bascule vers `ReaderScreen` au
 * clic sur un livre — état Compose simple ([AppScreen]), pas
 * `androidx.navigation` : deux écrans, pas de back stack profond ni de
 * deep links à gérer pour l'instant, une dépendance de navigation
 * complète serait prématurée.
 *
 * `BootstrapAndOpenFixture` (debug uniquement, `BuildConfig.DEBUG`) :
 * insère la publication fixture dans la bibliothèque mais ne force plus
 * l'ouverture directe du Reader — l'utilisateur (ou le testeur) la choisit
 * depuis `LibraryScreen` comme n'importe quel autre livre, pas de
 * court-circuit de la navigation réelle même en debug.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val readerViewModel: ReaderViewModel = hiltViewModel()

                    // BootstrapAndOpenFixture n'appelle plus que l'insertion +
                    // le parsing interne du ViewModel (Tache 7.1bis) - la
                    // navigation vers ReaderScreen se fait desormais via
                    // AppScreen, jamais automatiquement, meme en debug.
                    if (BuildConfig.DEBUG) {
                        LaunchedEffect(Unit) {
                            val fixtureFile = File(cacheDir, "fixture-marche-a-blanc.epub").apply {
                                if (!exists()) {
                                    assets.open("fixture-marche-a-blanc.epub").use { input ->
                                        outputStream().use { output -> input.copyTo(output) }
                                    }
                                }
                            }
                            readerViewModel.onIntent(
                                ReaderIntent.BootstrapAndOpenFixture(
                                    publicationId = WALKING_SKELETON_FIXTURE_PUBLICATION_ID,
                                    fileUri = fixtureFile.absolutePath,
                                ),
                            )
                        }
                    }

                    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Library) }

                    when (val current = screen) {
                        AppScreen.Library -> LibraryScreen(
                            onNavigateToReader = { publicationId -> screen = AppScreen.Reader(publicationId) },
                            floatingActionButton = {
                                Row {
                                    ImportPickerButton()
                                    Button(onClick = { screen = AppScreen.Search }) { Text("Rechercher") }
                                }
                            },
                        )
                        is AppScreen.Reader -> {
                            BackHandler { screen = AppScreen.Library }
                            LaunchedEffect(current.publicationId, current.targetResourceHref, current.targetChapterIndex, current.targetCharOffset) {
                                readerViewModel.onIntent(
                                    ReaderIntent.OpenPublication(
                                        publicationId = current.publicationId,
                                        targetResourceHref = current.targetResourceHref,
                                        targetChapterIndex = current.targetChapterIndex,
                                        targetCharOffset = current.targetCharOffset,
                                    ),
                                )
                            }
                            Column {
                                Button(onClick = { screen = AppScreen.Library }) { Text("< Bibliotheque") }
                                ReaderScreen(viewModel = readerViewModel)
                            }
                        }
                        AppScreen.Search -> {
                            BackHandler { screen = AppScreen.Library }
                            Column {
                                Button(onClick = { screen = AppScreen.Library }) { Text("< Bibliotheque") }
                                SearchScreen(
                                    onNavigateToReader = { publicationId, resourceHref, chapterIndex, charOffset ->
                                        screen = AppScreen.Reader(publicationId, resourceHref, chapterIndex, charOffset)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Tache 7.5 : targetResourceHref/targetChapterIndex/targetCharOffset en
    // primitifs, pas un Locator - ce module (app) n'a pas le droit de
    // dependre de domain directement (Blueprint §12.4). Voir le meme choix
    // sur ReaderIntent.OpenPublication (feature/reader), qui reconstruit le
    // Locator la ou domain est une dependance autorisee.
    private sealed interface AppScreen {
        data object Library : AppScreen
        data class Reader(
            val publicationId: String,
            val targetResourceHref: String? = null,
            val targetChapterIndex: Int? = null,
            val targetCharOffset: Int? = null,
        ) : AppScreen

        /** Tâche 7.5 — extension du même pattern léger, pas une nouvelle dépendance de navigation. */
        data object Search : AppScreen
    }

    private companion object {
        const val WALKING_SKELETON_FIXTURE_PUBLICATION_ID = "walking-skeleton-fixture"
    }
}
