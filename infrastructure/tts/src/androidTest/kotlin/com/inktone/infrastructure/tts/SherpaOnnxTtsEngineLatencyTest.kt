package com.inktone.infrastructure.tts

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import com.inktone.core.testing.fake.FakePronunciationRuleRepository
import com.inktone.domain.service.PronunciationRuleApplier
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Mesure la latence réelle de `synthesize()` (Kokoro + resampling + CTC,
 * Tâche 5.1/5.2 assemblées) sur un device physique — chronométrée avec un
 * nombre d'itérations borné (1 run initial + 5 répétitions), pas
 * `androidx.benchmark.measureRepeated` : ce dernier répète l'opération un
 * nombre de fois non borné jusqu'à stabilité statistique (conçu pour des
 * micro-opérations légères), ce qui a fait planter le process sur ce
 * device pour ce pipeline lourd (~200 itérations en ~210s avant crash,
 * cause exacte non élucidée - possible accumulation de ressources
 * natives sur un grand nombre d'appels). Même discipline de mesure que
 * `Int8LatencyTest` du prototype (`PROTOTYPE_ALIGNEMENT_CTC.md` §7.4).
 */
@RunWith(AndroidJUnit4::class)
class SherpaOnnxTtsEngineLatencyTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun stage(externalDirName: String, targetDir: File) {
        val staged = File(context.getExternalFilesDir(null), externalDirName)
        if (staged.exists()) {
            staged.copyRecursively(targetDir, overwrite = true)
        }
    }

    @Test
    fun mesure_latence_synthese_plus_alignement(): Unit = runBlocking {
        val modelPaths = SherpaOnnxModelPaths(context)
        val ctcModelPaths = CtcModelPaths(context)
        if (!modelPaths.isReady) stage("kokoro-int8-multi-lang-v1_0", modelPaths.modelFile.parentFile!!)
        if (!ctcModelPaths.isReady) stage("nemo-ctc-fr-multilang-int8", ctcModelPaths.modelFile.parentFile!!)
        assumeTrue("Modele Kokoro absent", modelPaths.isReady)
        assumeTrue("Modele CTC absent", ctcModelPaths.isReady)

        val engine = SherpaOnnxTtsEngine(modelPaths, CtcForcedAligner(ctcModelPaths), PronunciationRuleApplier(FakePronunciationRuleRepository()))
        val voiceProfile = VoiceProfile(id = "vp-sherpa-fr", engine = TtsEngineId.SHERPA_ONNX, voice = "ff_siwis", language = "fr-FR")
        val text = "Bonjour le monde. Ceci est un test pour vérifier l'alignement."
        val sentence = Sentence(index = 0, text = text, startOffset = 0, endOffset = text.length)

        val t0 = System.nanoTime()
        val first = engine.synthesize(sentence, voiceProfile)
        val firstMs = (System.nanoTime() - t0) / 1e6
        Log.i(TAG, "[LATENCY] premier appel (froid) = ${"%.2f".format(firstMs)} ms, " +
            "audio_duration_ms=${first.durationMs}, mots=${first.wordTimestamps.size}")

        val repeatsMs = mutableListOf<Double>()
        repeat(5) {
            val rt0 = System.nanoTime()
            engine.synthesize(sentence, voiceProfile)
            repeatsMs.add((System.nanoTime() - rt0) / 1e6)
        }
        Log.i(TAG, "[LATENCY] repetitions (ms) = ${repeatsMs.joinToString(",") { "%.2f".format(it) }}")
        Log.i(TAG, "[LATENCY] mediane = ${"%.2f".format(repeatsMs.sorted()[repeatsMs.size / 2])} ms")

        for (w in first.wordTimestamps) {
            Log.i(TAG, "[WORD] start=${w.startMs} end=${w.endMs} charOffset=${w.charOffset} word=${w.word}")
        }

        // Decomposition : synthese Kokoro seule vs alignement CTC seul
        // (deja mesure isolement a ~540ms dans le prototype scratchpad -
        // ici on verifie sur le pipeline production reel lequel des deux
        // domine le total mesure ci-dessus).
        val tSynth0 = System.nanoTime()
        val generated = engine.tts.generate(text = sentence.text, sid = 30, speed = voiceProfile.speed)
        val synthMs = (System.nanoTime() - tSynth0) / 1e6

        val aligner = CtcForcedAligner(ctcModelPaths)
        val tAlign0 = System.nanoTime()
        aligner.align(generated.samples, generated.sampleRate, sentence.text)
        val alignColdMs = (System.nanoTime() - tAlign0) / 1e6

        // Meme aligner, deuxieme appel : session ONNX deja chargee (lazy) -
        // isole le cout de chargement du modele du cout d'inference reel.
        val tAlign1 = System.nanoTime()
        aligner.align(generated.samples, generated.sampleRate, sentence.text)
        val alignWarmMs = (System.nanoTime() - tAlign1) / 1e6

        Log.i(
            TAG,
            "[LATENCY_BREAKDOWN] kokoro_synth_ms=${"%.2f".format(synthMs)} " +
                "ctc_align_cold_ms=${"%.2f".format(alignColdMs)} ctc_align_warm_ms=${"%.2f".format(alignWarmMs)}",
        )
    }

    private companion object {
        const val TAG = "SherpaOnnxTtsEngineLatencyTest"
    }
}
