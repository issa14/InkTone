package com.inktone.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.InkToneTheme
import com.inktone.core.designsystem.LocalWindowSizeClass
import com.inktone.feature.settings.AppThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Point d'entrée de l'app (Tâche 9bis.2, révision) — héberge maintenant
 * `InkToneNavHost` (routes typées) au lieu de l'état `AppScreen` à 3 cas
 * (Phase 7), lui-même une extension consciente de l'état minimal posé en
 * Tâche 7.1bis, pas une réécriture qui jette ce travail : `AppScreen
 * .Library`/`Reader`/`Search` deviennent `LibraryRoute`/`ReaderRoute`/
 * `SearchRoute` (`Routes.kt`), le back stack manuel (`BackHandler`
 * répété par écran) est remplacé par le back stack réel de `NavHost`.
 *
 * Lot 10 — le scaffolding de marche à blanc (`BootstrapAndOpenFixture`,
 * insertion automatique d'une publication fixture à chaque lancement
 * debug, Phase 3) est retiré : hérité d'avant que l'import réel
 * (`feature/import`) n'existe, il polluait silencieusement la
 * bibliothèque de tout build debug et rendait l'état vide impossible à
 * vérifier sur appareil (retour Issa). `ReaderIntent
 * .BootstrapAndOpenFixture`/`fixture-marche-a-blanc.epub` retirés avec.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Sous-lot 2d, D5 — le splash couvre exactement le frame vide que
        // `hasSeenOnboarding == null` contournait deja plus bas (Lot 10) :
        // il reste a l'ecran tant que la preference n'est pas chargee.
        var isReady by mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition { !isReady }
        setContent {
            // Tache 9.0.2 : calculee une seule fois ici, fournie a toute
            // l'arborescence via LocalWindowSizeClass (core:designsystem) -
            // fondation seulement, aucun layout existant ne la consomme
            // encore (pas de mode tablette double page, hors perimetre v1).
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                val appThemeViewModel: AppThemeViewModel = hiltViewModel()
                val useDynamicColor by appThemeViewModel.useDynamicColor.collectAsState()
                val appTheme by appThemeViewModel.appTheme.collectAsState()
                // Lot 10 — null tant que la préférence n'est pas encore
                // chargée depuis Room : un seul frame de retard plutôt
                // qu'un flash sur LibraryRoute avant redirection vers
                // l'onboarding (piège explicite du plan, Tâche 10.4).
                val hasSeenOnboarding by appThemeViewModel.hasSeenOnboarding.collectAsState()
                LaunchedEffect(hasSeenOnboarding) {
                    if (hasSeenOnboarding != null) isReady = true
                }
                InkToneTheme(useDynamicColor = useDynamicColor, appTheme = appTheme) {
                    Surface {
                        if (hasSeenOnboarding == null) return@Surface

                        InkToneNavHost(
                            startDestination = if (hasSeenOnboarding == true) LibraryRoute else OnboardingRoute,
                        )
                    }
                }
            }
        }
    }
}
