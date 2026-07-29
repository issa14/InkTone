package com.inktone.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
 *
 * Bouton « Importer » (Tâche 4.11) : sélecteur SAF minimal pour valider
 * le chemin réel `content://` -> `ReadiumPublicationParser` contre un
 * vrai EPUB, sans construire l'écran d'import complet de
 * `feature/import` (Phase 6).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val viewModel: ReaderViewModel = hiltViewModel()
                    // BootstrapAndOpenFixture reinsere systematiquement la
                    // meme Publication (meme id fixe) a chaque lancement -
                    // scaffolding de marche a blanc (Phase 3), jamais destine
                    // a un build de release. Ne doit jamais s'executer hors
                    // debug, point final (revue apres le bug
                    // OnConflictStrategy.REPLACE/CASCADE decouvert Tache 7.1 -
                    // meme sans REPLACE, reinserer un id fixe a chaque
                    // lancement resterait un artefact de test qui n'a rien
                    // a faire dans une build utilisateur).
                    if (BuildConfig.DEBUG) {
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
                    }

                    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri != null) {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            viewModel.onIntent(ReaderIntent.ImportAndOpen(uri.toString()))
                        }
                    }

                    Column(modifier = Modifier.padding(8.dp)) {
                        Button(onClick = { importLauncher.launch(arrayOf("application/epub+zip", "application/octet-stream")) }) {
                            Text("Importer (Tache 4.11)")
                        }
                        ReaderScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    private companion object {
        const val WALKING_SKELETON_FIXTURE_PUBLICATION_ID = "walking-skeleton-fixture"
    }
}
