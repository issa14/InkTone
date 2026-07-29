package com.inktone.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.feature.importer.ImportPickerButton
import com.inktone.feature.reader.ReaderIntent
import com.inktone.feature.reader.ReaderScreen
import com.inktone.feature.reader.ReaderViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

/**
 * Point d'entree minimal pour le test manuel de la Phase 4 : heberge
 * ReaderScreen et ouvre le fixture EPUB embarque (copie de
 * fixture-minimal.epub, Phase 3) via BootstrapAndOpenFixture (debug
 * uniquement). Pas de navigation reelle vers une bibliotheque — reste un
 * artefact plus ancien que ce que la Phase 6 aurait du produire ici (pas
 * corrige dans cette tache, signale separement).
 *
 * Injection par champ de PublicationRepository volontairement évitée
 * ici (KSP error.NonExistentClass, Tâche 3.7) : seule la copie de
 * fichier (pas d'accès Hilt) est faite dans cette Activity, le
 * bootstrap réel de la Publication passe par ReaderViewModel
 * (injection par constructeur, qui fonctionne).
 *
 * Import (Tâche 7.1bis) : `ImportPickerButton` (`feature/import`, Tâche
 * 6.2bis) remplace le sélecteur SAF ad hoc de la Tâche 4.11
 * (`ReaderIntent.ImportAndOpen`, retiré) — vrai pipeline `ImportWorker` +
 * détection de doublons par hash, pas un raccourci qui contournait
 * `ImportPublicationUseCase`. N'ouvre plus automatiquement le livre
 * importé dans `ReaderScreen` : l'import est maintenant asynchrone
 * (WorkManager) et il n'existe encore aucune navigation vers une
 * bibliothèque pour choisir quoi ouvrir ensuite (voir plus haut).
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

                    Column(modifier = Modifier.padding(8.dp)) {
                        ImportPickerButton()
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
