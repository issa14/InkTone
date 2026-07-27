package com.inktone.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.feature.reader.ReaderIntent
import com.inktone.feature.reader.ReaderScreen
import com.inktone.feature.reader.ReaderViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

/**
 * Point d'entree minimal pour le test manuel de la Phase 4 : heberge
 * ReaderScreen et ouvre le fixture EPUB embarque (copie de
 * fixture-minimal.epub, Phase 3) via BootstrapAndOpenFixture. Pas de
 * navigation reelle vers une bibliotheque — Phase 6 remplacera ceci par
 * le graphe de navigation complet et l'import SAF reel.
 *
 * Injection par champ de PublicationRepository volontairement évitée
 * ici (KSP error.NonExistentClass, Tâche 3.7) : seule la copie de
 * fichier (pas d'accès Hilt) est faite dans cette Activity, le
 * bootstrap réel de la Publication passe par ReaderViewModel
 * (injection par constructeur, qui fonctionne).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val viewModel: ReaderViewModel = hiltViewModel()
                    LaunchedEffect(Unit) {
                        val fixtureFile = File(cacheDir, "fixture-marche-a-blanc.epub").apply {
                            if (!exists()) {
                                assets.open("fixture-marche-a-blanc.epub").use { input ->
                                    outputStream().use { output -> input.copyTo(output) }
                                }
                            }
                        }
                        viewModel.onIntent(
                            ReaderIntent.BootstrapAndOpenFixture(
                                publicationId = WALKING_SKELETON_FIXTURE_PUBLICATION_ID,
                                fileUri = fixtureFile.absolutePath,
                            ),
                        )
                    }
                    ReaderScreen(viewModel = viewModel)
                }
            }
        }
    }

    private companion object {
        const val WALKING_SKELETON_FIXTURE_PUBLICATION_ID = "walking-skeleton-fixture"
    }
}
