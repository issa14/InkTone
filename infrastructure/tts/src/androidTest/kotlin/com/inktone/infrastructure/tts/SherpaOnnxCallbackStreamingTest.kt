package com.inktone.infrastructure.tts

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.k2fsa.sherpa.onnx.GenerationConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Diagnostic : `generateWithConfigAndCallback` livre-t-il l'audio de
 * maniere reellement incrementale, ou le callback ne se declenche-t-il
 * qu'une seule fois a la toute fin (API de callback sans streaming reel) ?
 * Question posee avant de conclure a une limite architecturale necessitant
 * `TtsEngine.synthesize()` -> flux de chunks (changement de contrat
 * domaine) - voir `docs/execution/PHASE_5_TTS_ENGINE.md`, section
 * "Diagnostic de la latence Kokoro". Chaque appel du callback est
 * horodate individuellement (pas seulement le retour final) : c'est le
 * PREMIER appel, pas le dernier, qui serait le candidat pour le budget
 * tap -> premier audio (Blueprint SS11.2, <= 1500 ms).
 *
 * Meme phrase et meme protocole que `SherpaOnnxTtsEngineLatencyTest`
 * (numThreads=4, provider=cpu, device V2206) pour rester comparable au
 * RTF ~4,7x deja mesure.
 */
@RunWith(AndroidJUnit4::class)
class SherpaOnnxCallbackStreamingTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun stage(externalDirName: String, targetDir: File) {
        val staged = File(context.getExternalFilesDir(null), externalDirName)
        if (staged.exists()) {
            staged.copyRecursively(targetDir, overwrite = true)
        }
    }

    @Test
    fun trajectoire_des_appels_du_callback(): Unit = runBlocking {
        val modelPaths = SherpaOnnxModelPaths(context)
        if (!modelPaths.isReady) stage("kokoro-int8-multi-lang-v1_0", modelPaths.modelFile.parentFile!!)
        assumeTrue("Modele Kokoro absent", modelPaths.isReady)

        val engine = SherpaOnnxTtsEngine(modelPaths, ctcForcedAligner = CtcForcedAligner(CtcModelPaths(context)))
        val text = "Bonjour le monde. Ceci est un test pour vérifier l'alignement."
        val config = GenerationConfig(speed = 1.0f, sid = 30)

        // Un appel a froid (chargement modele inclus) + 3 appels a chaud -
        // meme discipline que SherpaOnnxTtsEngineLatencyTest (mesure bornee,
        // pas measureRepeated qui a deja fait planter le process sur ce
        // device pour ce pipeline).
        repeat(4) { run ->
            data class Call(val elapsedMs: Double, val samples: Int)

            val calls = mutableListOf<Call>()
            val t0 = System.nanoTime()

            // object : (FloatArray) -> Int, pas une lambda trainante : le
            // JNI de generateWithConfigImpl invoque la callback par
            // reflection sur une signature Kotlin Function1 concrete
            // (invoke([F)Ljava/lang/Integer;) - la lambda indy (defaut
            // Kotlin 2.x, pas de classe synthetique concrete generee a la
            // compilation) plante en NoSuchMethodError/SIGABRT sur ce
            // build/device, verifie en pratique avant ce contournement.
            val callback = object : Function1<FloatArray, Int> {
                override fun invoke(samples: FloatArray): Int {
                    calls.add(Call((System.nanoTime() - t0) / 1e6, samples.size))
                    return 1 // continue la generation
                }
            }

            val generated = engine.tts.generateWithConfigAndCallback(
                text = text,
                config = config,
                callback = callback,
            )

            val totalMs = (System.nanoTime() - t0) / 1e6

            Log.i(
                TAG,
                "[RUN $run] nb_appels_callback=${calls.size} total_ms=${"%.2f".format(totalMs)} " +
                    "audio_samples=${generated.samples.size} sample_rate=${generated.sampleRate}",
            )
            calls.forEachIndexed { i, c ->
                Log.i(TAG, "[RUN $run][CALLBACK $i] elapsed_ms=${"%.2f".format(c.elapsedMs)} samples=${c.samples}")
            }
            if (calls.isNotEmpty()) {
                Log.i(
                    TAG,
                    "[RUN $run] PREMIER_APPEL_ms=${"%.2f".format(calls.first().elapsedMs)} " +
                        "DERNIER_APPEL_ms=${"%.2f".format(calls.last().elapsedMs)} " +
                        "ecart_premier_dernier_ms=${"%.2f".format(calls.last().elapsedMs - calls.first().elapsedMs)}",
                )
            }
        }
    }

    private companion object {
        const val TAG = "SherpaOnnxCallbackStreamingTest"
    }
}
