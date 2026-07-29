package com.inktone.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.InkToneTheme
import com.inktone.core.designsystem.LocalWindowSizeClass
import com.inktone.feature.reader.ReaderIntent
import com.inktone.feature.reader.ReaderViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

/**
 * Point d'entrée de l'app (Tâche 9bis.2, révision) — héberge maintenant
 * `InkToneNavHost` (routes typées) au lieu de l'état `AppScreen` à 3 cas
 * (Phase 7), lui-même une extension consciente de l'état minimal posé en
 * Tâche 7.1bis, pas une réécriture qui jette ce travail : `AppScreen
 * .Library`/`Reader`/`Search` deviennent `LibraryRoute`/`ReaderRoute`/
 * `SearchRoute` (`Routes.kt`), le back stack manuel (`BackHandler`
 * répété par écran) est remplacé par le back stack réel de `NavHost`.
 *
 * `BootstrapAndOpenFixture` (debug uniquement, `BuildConfig.DEBUG`) :
 * insère la publication fixture dans la bibliothèque via une instance de
 * `ReaderViewModel` dédiée à ce seul effet, distincte de celle que
 * `ReaderRoute` créera plus tard (scoped à son `NavBackStackEntry`) —
 * l'insertion passe par Room (Tâche 6.1), donc visible de l'une comme de
 * l'autre, aucun état partagé requis entre les deux instances.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Tache 9.0.2 : calculee une seule fois ici, fournie a toute
            // l'arborescence via LocalWindowSizeClass (core:designsystem) -
            // fondation seulement, aucun layout existant ne la consomme
            // encore (pas de mode tablette double page, hors perimetre v1).
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                InkToneTheme {
                    Surface {
                        if (BuildConfig.DEBUG) {
                            val bootstrapViewModel: ReaderViewModel = hiltViewModel()
                            LaunchedEffect(Unit) {
                                val fixtureFile = File(cacheDir, "fixture-marche-a-blanc.epub").apply {
                                    if (!exists()) {
                                        assets.open("fixture-marche-a-blanc.epub").use { input ->
                                            outputStream().use { output -> input.copyTo(output) }
                                        }
                                    }
                                }
                                bootstrapViewModel.onIntent(
                                    ReaderIntent.BootstrapAndOpenFixture(
                                        publicationId = WALKING_SKELETON_FIXTURE_PUBLICATION_ID,
                                        fileUri = fixtureFile.absolutePath,
                                    ),
                                )
                            }
                        }

                        InkToneNavHost()
                    }
                }
            }
        }
    }

    private companion object {
        const val WALKING_SKELETON_FIXTURE_PUBLICATION_ID = "walking-skeleton-fixture"
    }
}
