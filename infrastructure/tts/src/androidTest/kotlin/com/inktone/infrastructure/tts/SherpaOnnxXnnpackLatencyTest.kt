package com.inktone.infrastructure.tts

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Dernier levier non teste du diagnostic latence Kokoro (`PHASE_5_TTS_ENGINE.md`) :
 * XNNPACK, absent du `.so` vendore (verifie explicitement en Tache diagnostic,
 * "Available providers: NnapiExecutionProvider, CPUExecutionProvider" - pas
 * de Xnnpack). Contrairement a NNAPI (juste un guard de compilation sur le
 * binaire sherpa-onnx JNI prebuilt), XNNPACK doit etre compile DANS
 * ONNX Runtime lui-meme (`--use_xnnpack`, pas un flag sherpa-onnx) - ONNX
 * Runtime v1.27.0 reconstruit depuis les sources ici (host, hors device,
 * NDK/cmake), `libsherpa-onnx-jni.so` relie contre ce nouveau
 * `libonnxruntime.so`. `.so` vendores de production remplaces
 * TEMPORAIREMENT pour cette mesure uniquement (restaures et sha256
 * revérifiés juste apres, voir `docs/execution/PHASE_5_TTS_ENGINE.md`).
 *
 * Meme protocole exact que les diagnostics precedents (meme phrase, meme
 * device V2206, `numThreads=4`), seul `provider` change (`xnnpack` au lieu
 * de `cpu`).
 */
@RunWith(AndroidJUnit4::class)
class SherpaOnnxXnnpackLatencyTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun mesure_rtf_kokoro_xnnpack(): Unit = runBlocking {
        val modelPaths = SherpaOnnxModelPaths(context)
        if (!modelPaths.isReady) {
            val staged = File(context.getExternalFilesDir(null), "kokoro-int8-multi-lang-v1_0")
            if (staged.exists()) staged.copyRecursively(modelPaths.modelFile.parentFile!!, overwrite = true)
        }
        assumeTrue("Modele Kokoro absent", modelPaths.isReady)

        val tts = OfflineTts(
            assetManager = null,
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = modelPaths.modelFile.absolutePath,
                        voices = modelPaths.voicesFile.absolutePath,
                        tokens = modelPaths.tokensFile.absolutePath,
                        dataDir = modelPaths.espeakDataDir.absolutePath,
                        lexicon = "${modelPaths.lexiconUsEnFile.absolutePath},${modelPaths.lexiconZhFile.absolutePath}",
                    ),
                    numThreads = 4,
                    provider = "xnnpack",
                    debug = true,
                ),
                ruleFsts = "${modelPaths.phoneZhFst.absolutePath},${modelPaths.dateZhFst.absolutePath},${modelPaths.numberZhFst.absolutePath}",
            ),
        )

        val text = "Bonjour le monde. Ceci est un test pour vérifier l'alignement."
        val sid = 30

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
    }

    private companion object {
        const val TAG = "SherpaOnnxXnnpackLatencyTest"
    }
}
