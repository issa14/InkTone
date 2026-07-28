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
 * Réévalue `vits-piper-fr_FR-upmc-medium` (modèle legacy, VITS/Piper,
 * 73 Mo, 2 locuteurs Jessica/Pierre) **sur le pipeline actuel**
 * (`OfflineTts` de la même dépendance `sherpa-onnx` déjà vendorée pour
 * Kokoro, pas le moteur legacy) — le RTF ~0,33 cité dans
 * `PROJECT_STATUS.md`/`architecture.md` du monolithe archivé
 * (`legacy/monolith`) est une affirmation de commentaire d'une
 * implémentation différente, jamais mesurée ici. Même discipline que
 * `SherpaOnnxTtsEngineLatencyTest` (1 run froid + 5 répétitions, pas
 * `measureRepeated`) et même protocole que les diagnostics Kokoro
 * (`numThreads=4`, `provider=cpu`, même device V2206, même phrase de
 * test) pour rester directement comparable.
 *
 * Construit `OfflineTts` directement avec une `OfflineTtsVitsModelConfig`
 * (pas via `SherpaOnnxTtsEngine`, dont le `tts` lazy est câblé
 * spécifiquement sur les chemins Kokoro/`SherpaOnnxModelPaths`) — même
 * classe `OfflineTts`, même `.so` vendoré, seul le modèle change.
 */
@RunWith(AndroidJUnit4::class)
class SherpaOnnxPiperUpmcLatencyTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val modelDir = File(context.filesDir, "voices/vits-piper-fr_FR-upmc-medium")
    private val modelFile = File(modelDir, "fr_FR-upmc-medium.onnx")
    private val tokensFile = File(modelDir, "tokens.txt")
    private val espeakDataDir = File(modelDir, "espeak-ng-data")

    private val isReady: Boolean
        get() = modelFile.exists() && tokensFile.exists() && espeakDataDir.exists()

    private fun stage() {
        val staged = File(context.getExternalFilesDir(null), "vits-piper-fr_FR-upmc-medium")
        if (staged.exists()) {
            staged.copyRecursively(modelDir, overwrite = true)
        }
    }

    @Test
    fun mesure_rtf_piper_upmc_et_compatibilite_ctc(): Unit = runBlocking {
        if (!isReady) stage()
        assumeTrue("Modele Piper UPMC absent", isReady)

        val ctcModelPaths = CtcModelPaths(context)
        if (!ctcModelPaths.isReady) {
            val staged = File(context.getExternalFilesDir(null), "nemo-ctc-fr-multilang-int8")
            if (staged.exists()) staged.copyRecursively(ctcModelPaths.modelFile.parentFile!!, overwrite = true)
        }
        assumeTrue("Modele CTC absent", ctcModelPaths.isReady)

        val tts = OfflineTts(
            assetManager = null,
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = modelFile.absolutePath,
                        lexicon = "", // phonemisation espeak, pas de lexique - meme convention que les exemples officiels piper (NonStreamingTtsPiperEn.java)
                        tokens = tokensFile.absolutePath,
                        dataDir = espeakDataDir.absolutePath,
                    ),
                    // Meme reglage que le diagnostic Kokoro (deja confirme optimal
                    // sur ce device, PHASE_5_TTS_ENGINE.md) - comparable a isoconfig.
                    numThreads = 4,
                    provider = "cpu",
                ),
            ),
        )

        val text = "Bonjour le monde. Ceci est un test pour vérifier l'alignement."
        // sid=0 -> Jessica (voix feminine), confirme via speaker_id_map du
        // fr_FR-upmc-medium.onnx.json ({"jessica": 0, "pierre": 1}) - pas devine.
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

        // Compatibilite pipeline CTC : verifie par un vrai run sur l'audio
        // REELLEMENT produit par ce modele (pas suppose indifferent au moteur
        // source parce que le CTC ne consomme que audioSamples/sampleRate).
        val aligner = CtcForcedAligner(ctcModelPaths)
        val tAlign0 = System.nanoTime()
        val wordTimestamps = aligner.align(
            audioSamples = first.samples,
            sampleRate = first.sampleRate,
            referenceText = text,
        )
        val alignMs = (System.nanoTime() - tAlign0) / 1e6
        Log.i(TAG, "[CTC] align_ms=${"%.2f".format(alignMs)} nb_mots=${wordTimestamps.size}")
        for (w in wordTimestamps) {
            Log.i(TAG, "[CTC][WORD] start=${w.startMs} end=${w.endMs} charOffset=${w.charOffset} word=${w.word}")
        }
    }

    /**
     * Exporte les deux voix (Jessica sid=0, Pierre sid=1) en WAV sur le
     * stockage externe pour ecoute humaine - l'evaluation qualite (barre
     * 8/10 deja appliquee a Kokoro) ne peut pas etre faite par ce test,
     * seulement produire les fichiers a ecouter.
     */
    @Test
    fun exporte_echantillons_wav_pour_ecoute(): Unit = runBlocking {
        if (!isReady) stage()
        assumeTrue("Modele Piper UPMC absent", isReady)

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
        val outDir = File(context.getExternalFilesDir(null), "piper_upmc_samples")
        outDir.mkdirs()

        val jessica = tts.generate(text = text, sid = 0, speed = 1.0f)
        jessica.save(File(outDir, "jessica.wav").absolutePath)

        val pierre = tts.generate(text = text, sid = 1, speed = 1.0f)
        pierre.save(File(outDir, "pierre.wav").absolutePath)

        Log.i(TAG, "[EXPORT] wav ecrits dans ${outDir.absolutePath}")
    }

    private companion object {
        const val TAG = "SherpaOnnxPiperUpmcLatencyTest"
    }
}
