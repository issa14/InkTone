package com.inktone.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.feature.reader.AudioSegmentPlayer
import com.inktone.feature.reader.ReaderIntent
import com.inktone.feature.reader.ReaderScreen
import com.inktone.feature.reader.ReaderViewModel
import com.inktone.infrastructure.tts.SherpaOnnxModelPaths
import com.inktone.infrastructure.tts.SherpaOnnxTtsEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
 *
 * Bouton « Tester Palier 2 » (Tâche 5.1) : scaffolding de validation
 * pour l'adaptateur Sherpa-ONNX — copie le modèle vocal depuis les
 * assets locaux (non committés, `.gitignore`) vers le stockage privé de
 * l'app, puis synthétise et joue une phrase réelle. `SherpaOnnxTtsEngine`
 * et `SherpaOnnxModelPaths` sont instanciés directement (sans Hilt) :
 * ce test n'a pas vocation à rester après que Tâche 5.6 (téléchargement
 * vérifié) et 5.4/5.5 (lecture via MediaSession) remplacent ce chemin.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val viewModel: ReaderViewModel = hiltViewModel()
                    val coroutineScope = rememberCoroutineScope()

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
                        Button(onClick = { coroutineScope.launch { testSherpaOnnx() } }) {
                            Text("Tester Palier 2")
                        }
                        ReaderScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    private suspend fun testSherpaOnnx() {
        val modelPaths = SherpaOnnxModelPaths(applicationContext)
        if (!modelPaths.isReady) {
            copyAssetDirRecursively("models/vits-piper-fr_FR-siwis-medium", modelPaths.modelFile.parentFile!!)
        }
        val engine = SherpaOnnxTtsEngine(modelPaths)
        val sentence = Sentence(
            index = 0,
            text = "— Bonjour, dit-elle, êtes-vous l'homme qui peut-être m'attendait ?",
            startOffset = 0,
            endOffset = 66,
        )
        val voiceProfile = VoiceProfile(
            id = "vp-sherpa-fr", engine = TtsEngineId.SHERPA_ONNX,
            voice = "fr_FR-siwis-medium", language = "fr-FR",
        )
        Log.i("MainActivity", "Palier 2 - synthese en cours...")
        val segment = engine.synthesize(sentence, voiceProfile)
        Log.i(
            "MainActivity",
            "Palier 2 - segment produit : sampleRate=${segment.sampleRate} durationMs=${segment.durationMs} " +
                "audioBytes=${segment.audioData.size}",
        )
        AudioSegmentPlayer().play(segment)
    }

    private fun copyAssetDirRecursively(assetPath: String, targetDir: File) {
        targetDir.mkdirs()
        val children = assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            assets.open(assetPath).use { input ->
                File(targetDir.parentFile, assetPath.substringAfterLast('/')).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return
        }
        for (child in children) {
            val childAssetPath = "$assetPath/$child"
            val grandChildren = assets.list(childAssetPath) ?: emptyArray()
            if (grandChildren.isEmpty()) {
                assets.open(childAssetPath).use { input ->
                    File(targetDir, child).outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                copyAssetDirRecursively(childAssetPath, File(targetDir, child))
            }
        }
    }

    private companion object {
        const val WALKING_SKELETON_FIXTURE_PUBLICATION_ID = "walking-skeleton-fixture"
    }
}
