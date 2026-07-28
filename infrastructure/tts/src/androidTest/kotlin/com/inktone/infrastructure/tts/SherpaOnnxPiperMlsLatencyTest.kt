package com.inktone.infrastructure.tts

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Mesure `fr_FR-mls-medium` (catalogue `rhasspy/piper-voices`) — seul
 * candidat français du catalogue Piper **entraîné from scratch** (pas de
 * chaîne de provenance via une voix anglaise de base) sur un dataset
 * CC-BY 4.0 pur (Multilingual LibriSpeech, `openslr.org/94`, 125
 * locuteurs) : provenance de licence propre, contrairement à
 * `upmc-medium` (`SherpaOnnxPiperUpmcLatencyTest`, dataset CC-BY-SA 4.0
 * + finetune depuis la voix anglaise `lessac`). Voir
 * `docs/execution/PHASE_5_TTS_ENGINE.md`, section dédiée, pour le
 * tableau comparatif de toutes les voix françaises du catalogue.
 *
 * Fichier `.onnx` téléchargé brut depuis Hugging Face n'est PAS
 * directement utilisable par `sherpa-onnx` : il lui manque les
 * métadonnées ONNX (`model_type`/`comment`/`n_speakers`/`sample_rate`)
 * que le script officiel `scripts/piper/add_meta_data.py` du dépôt
 * `sherpa-onnx` injecte avant publication du modèle "vendoré" (confirmé
 * en comparant aux métadonnées présentes dans le `.onnx` déjà vendoré de
 * `upmc-medium`) — régénérées ici hors device (script Python, pas
 * embarqué dans ce test) avant de pousser le modèle sur le device.
 *
 * Même protocole exact que `SherpaOnnxPiperUpmcLatencyTest` (même classe
 * `OfflineTts`, même `.so` vendoré, `numThreads=4`/`provider=cpu`, même
 * phrase de test, même device V2206, même discipline 1 run froid + 5
 * répétitions).
 */
@RunWith(AndroidJUnit4::class)
class SherpaOnnxPiperMlsLatencyTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val modelDir = File(context.filesDir, "voices/vits-piper-fr_FR-mls-medium")
    private val modelFile = File(modelDir, "fr_FR-mls-medium.onnx")
    private val tokensFile = File(modelDir, "tokens.txt")
    private val espeakDataDir = File(modelDir, "espeak-ng-data")

    private val isReady: Boolean
        get() = modelFile.exists() && tokensFile.exists() && espeakDataDir.exists()

    private fun stage() {
        val staged = File(context.getExternalFilesDir(null), "vits-piper-fr_FR-mls-medium")
        if (staged.exists()) {
            staged.copyRecursively(modelDir, overwrite = true)
        }
    }

    @Test
    fun mesure_rtf_piper_mls(): Unit = runBlocking {
        if (!isReady) stage()
        assumeTrue("Modele Piper MLS absent", isReady)

        val tts = OfflineTts(
            assetManager = null,
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = modelFile.absolutePath,
                        lexicon = "",
                        tokens = tokensFile.absolutePath,
                        dataDir = espeakDataDir.absolutePath,
                    ),
                    numThreads = 4,
                    provider = "cpu",
                ),
            ),
        )

        val text = "Bonjour le monde. Ceci est un test pour vérifier l'alignement."
        // sid=0 -> locuteur MLS anonymise "1840" (speaker_id_map du json,
        // premiere entree) - pas de nom/genre associe dans les metadonnees
        // Piper pour ce modele multi-locuteurs (contrairement a
        // jessica/pierre d'upmc-medium) : c'est le meme locuteur que le
        // modele separe "fr_FR-mls_1840-low" du catalogue (extrait de ce
        // meme jeu multi-locuteurs).
        val sid = 0

        val t0 = System.nanoTime()
        val first = tts.generate(text = text, sid = sid, speed = 1.0f)
        val firstMs = (System.nanoTime() - t0) / 1e6
        val audioDurationMs = (first.samples.size.toLong() * 1000L) / first.sampleRate
        val firstRtf = firstMs / audioDurationMs
        Log.i(
            TAG,
            "[RTF] premier appel (froid) = ${"%.2f".format(firstMs)} ms, audio_duration_ms=$audioDurationMs, " +
                "sample_rate=${first.sampleRate}, RTF=${"%.3f".format(firstRtf)}",
        )

        val repeatsMs = mutableListOf<Double>()
        repeat(5) {
            val rt0 = System.nanoTime()
            tts.generate(text = text, sid = sid, speed = 1.0f)
            repeatsMs.add((System.nanoTime() - rt0) / 1e6)
        }
        val medianMs = repeatsMs.sorted()[repeatsMs.size / 2]
        val medianRtf = medianMs / audioDurationMs
        Log.i(TAG, "[RTF] repetitions (ms) = ${repeatsMs.joinToString(",") { "%.2f".format(it) }}")
        Log.i(TAG, "[RTF] mediane_ms=${"%.2f".format(medianMs)} RTF_median=${"%.3f".format(medianRtf)}")

        // Export WAV pour ecoute humaine - meme raison que pour upmc-medium,
        // la qualite percue ne peut pas etre mesuree par ce test.
        val outDir = File(context.getExternalFilesDir(null), "piper_mls_samples")
        outDir.mkdirs()
        first.save(File(outDir, "mls_sid0.wav").absolutePath)
        Log.i(TAG, "[EXPORT] wav ecrit dans ${outDir.absolutePath}")
    }

    /**
     * Exporte quelques locuteurs supplementaires (repartis sur les 125
     * disponibles, pas seulement sid=0) pour permettre une ecoute
     * comparative avant de juger la qualite du modele dans son ensemble -
     * un seul echantillon ne suffit pas a caracteriser 125 locuteurs
     * anonymises (voir section "Qualite vocale" du rapport).
     */
    @Test
    fun exporte_plusieurs_locuteurs_pour_ecoute(): Unit = runBlocking {
        if (!isReady) stage()
        assumeTrue("Modele Piper MLS absent", isReady)

        val tts = OfflineTts(
            assetManager = null,
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = modelFile.absolutePath,
                        lexicon = "",
                        tokens = tokensFile.absolutePath,
                        dataDir = espeakDataDir.absolutePath,
                    ),
                    numThreads = 4,
                    provider = "cpu",
                ),
            ),
        )

        val text = "Bonjour le monde. Ceci est un test pour vérifier l'alignement."
        val outDir = File(context.getExternalFilesDir(null), "piper_mls_samples")
        outDir.mkdirs()

        // Repartis sur l'intervalle [0, 124] (125 locuteurs, speaker_id_map
        // du json) - pas une selection curatee (aucune metadonnee de
        // genre/nom disponible pour ce modele), juste un echantillonnage
        // pour la comparaison a l'ecoute.
        for (sid in listOf(0, 20, 40, 60, 80, 100, 124)) {
            val generated = tts.generate(text = text, sid = sid, speed = 1.0f)
            generated.save(File(outDir, "mls_sid$sid.wav").absolutePath)
            Log.i(TAG, "[EXPORT] mls_sid$sid.wav ecrit")
        }
    }

    private companion object {
        const val TAG = "SherpaOnnxPiperMlsLatencyTest"
    }
}
