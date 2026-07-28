package com.inktone.infrastructure.tts

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Garde-fou de régression pour les budgets de latence TTS **réellement
 * mesurés et actés dans ADR-022** — pas un micro-benchmark exploratoire
 * ni un placeholder. Utilisait `androidx.benchmark.junit4.BenchmarkRule`
 * (`measureRepeated`) avant cette tâche : ce dernier répète l'opération
 * un nombre de fois non borné jusqu'à stabilité statistique, déjà
 * documenté ailleurs comme faisant planter le process sur ce device pour
 * le pipeline Kokoro (`SherpaOnnxTtsEngineLatencyTest`, §diagnostic) —
 * remplacé ici par la même discipline bornée (1 run à chaud d'échauffement
 * + répétitions comptées, `System.nanoTime`), déjà éprouvée sur ce
 * pipeline dans le reste du module.
 *
 * Références (ADR-022, mesures réelles device V2206, Snapdragon 680,
 * `docs/execution/PHASE_5_TTS_ENGINE.md`) :
 *  - **Palier 1** (Android natif) : ~179 ms
 *  - **Palier 2** (Kokoro, CPU+4 threads, meilleure configuration mesurée
 *    après épuisement de six leviers de configuration/matériel) : RTF ~4,7×
 *
 * Les seuils ci-dessous gardent une marge large (pas la valeur exacte
 * ADR-022) : ce test détecte une régression réelle (code cassé, provider
 * changé par erreur, modèle substitué) — pas la variance normale de
 * mesure sur un device physique partagé.
 */
@RunWith(AndroidJUnit4::class)
class TtsSynthesisBenchmarkTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val text = "Bonjour, ceci est une phrase de taille representative pour mesurer la latence de synthese."
    private val sentence = Sentence(index = 0, text = text, startOffset = 0, endOffset = text.length)

    @Test
    fun latence_synthese_palier1_android_natif_dans_l_ordre_de_grandeur_documente(): Unit = runBlocking {
        val engine = AndroidNativeTtsEngine(context)
        val voiceProfile = VoiceProfile(id = "vp-native-fr", engine = TtsEngineId.ANDROID_NATIVE, voice = "fr-fr-default", language = "fr-FR")

        engine.synthesize(sentence, voiceProfile) // echauffement, ecarte du chiffre retenu

        val repeatsMs = mutableListOf<Double>()
        repeat(5) {
            val t0 = System.nanoTime()
            engine.synthesize(sentence, voiceProfile)
            repeatsMs.add((System.nanoTime() - t0) / 1e6)
        }
        val medianMs = repeatsMs.sorted()[repeatsMs.size / 2]
        Log.i(TAG, "[BENCHMARK] palier1_android_natif mediane_ms=${"%.2f".format(medianMs)} (reference ADR-022 : ~179 ms)")

        assertTrue(
            "Palier 1 mesure ${"%.2f".format(medianMs)} ms, tres au-dessus de la reference ADR-022 (~179 ms) - regression a investiguer",
            medianMs < PALIER1_REGRESSION_THRESHOLD_MS,
        )
    }

    @Test
    fun latence_synthese_palier2_sherpa_onnx_dans_l_ordre_de_grandeur_documente(): Unit = runBlocking {
        val modelPaths = SherpaOnnxModelPaths(context)
        val stagedKokoro = File(context.getExternalFilesDir(null), "kokoro-int8-multi-lang-v1_0")
        if (!modelPaths.isReady && stagedKokoro.exists()) {
            stagedKokoro.copyRecursively(modelPaths.modelFile.parentFile!!, overwrite = true)
        }
        val ctcModelPaths = CtcModelPaths(context)
        val stagedCtc = File(context.getExternalFilesDir(null), "nemo-ctc-fr-multilang-int8")
        if (!ctcModelPaths.isReady && stagedCtc.exists()) {
            stagedCtc.copyRecursively(ctcModelPaths.modelFile.parentFile!!, overwrite = true)
        }
        assumeTrue(
            "Modele vocal Sherpa-ONNX absent - placer manuellement avant ce benchmark (Tache 5.6 le remplacera)",
            modelPaths.isReady,
        )
        assumeTrue(
            "Modele d'alignement CTC absent - placer manuellement avant ce benchmark (Tache 5.6 le remplacera)",
            ctcModelPaths.isReady,
        )
        val engine = SherpaOnnxTtsEngine(modelPaths, CtcForcedAligner(ctcModelPaths))
        val voiceProfile = VoiceProfile(id = "vp-sherpa-fr", engine = TtsEngineId.SHERPA_ONNX, voice = "ff_siwis", language = "fr-FR")

        val warm = engine.synthesize(sentence, voiceProfile) // echauffement (charge le modele), ecarte du chiffre retenu
        val audioDurationMs = warm.durationMs.coerceAtLeast(1L)

        // 3 repetitions, pas 5 : pipeline lourd (~20-25s/appel a ce RTF),
        // meme discipline bornee que SherpaOnnxTtsEngineLatencyTest -
        // measureRepeated (non borne) a deja fait planter le process pour
        // ce pipeline sur ce device.
        val repeatsMs = mutableListOf<Double>()
        repeat(3) {
            val t0 = System.nanoTime()
            engine.synthesize(sentence, voiceProfile)
            repeatsMs.add((System.nanoTime() - t0) / 1e6)
        }
        val medianMs = repeatsMs.sorted()[repeatsMs.size / 2]
        val rtf = medianMs / audioDurationMs
        Log.i(
            TAG,
            "[BENCHMARK] palier2_sherpa_onnx mediane_ms=${"%.2f".format(medianMs)} RTF=${"%.3f".format(rtf)} " +
                "(reference ADR-022 : RTF ~4.7x, CPU+4 threads)",
        )

        assertTrue(
            "Palier 2 mesure RTF ${"%.3f".format(rtf)}, tres au-dessus de la reference ADR-022 (~4,7x, deja un signal architectural connu et accepte) - regression a investiguer",
            rtf < PALIER2_REGRESSION_THRESHOLD_RTF,
        )
    }

    private companion object {
        const val TAG = "TtsSynthesisBenchmarkTest"
        const val PALIER1_REGRESSION_THRESHOLD_MS = 1_000.0
        const val PALIER2_REGRESSION_THRESHOLD_RTF = 10.0
    }
}
