package com.inktone.infrastructure.tts

import android.content.Context
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Microbenchmark (Tâche 5.9) — latence de `synthesize()` pour une phrase
 * de taille représentative, par moteur. Mesure directe en process
 * (`androidx.benchmark:benchmark-junit4`), plus adaptée ici que le
 * module `benchmark` (Tâche 4.9, macrobenchmark orienté UI/démarrage
 * d'app) puisqu'aucun parcours UI de lecture n'existe encore pour
 * déclencher une synthèse via `feature/player`.
 *
 * Pour le Palier 2, `synthesize()` inclut désormais l'alignement forcé
 * CTC (Tâche 5.2, branchée pour de vrai) — ce microbenchmark mesure donc
 * la latence de synthèse **+ alignement** combinée, pas la synthèse
 * seule. Chiffres réels sur device (Snapdragon 680, V2206) :
 * `docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md` §9. Reste une portée
 * volontairement partielle : ceci mesure un proxy de "latence premier
 * audio" (§11.2), PAS le silence inter-phrases (dépend du parcours de
 * lecture continue via `SentenceAudioBuffer`, Tâche 5.3, analysé mais
 * pas mesuré en conditions UI réelles ici).
 */
@RunWith(AndroidJUnit4::class)
class TtsSynthesisBenchmarkTest {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val text = "Bonjour, ceci est une phrase de taille representative pour mesurer la latence de synthese."
    private val sentence = Sentence(index = 0, text = text, startOffset = 0, endOffset = text.length)

    @Test
    fun latence_synthese_palier1_android_natif() {
        val engine = AndroidNativeTtsEngine(context)
        val voiceProfile = VoiceProfile(id = "vp-native-fr", engine = TtsEngineId.ANDROID_NATIVE, voice = "fr-fr-default", language = "fr-FR")

        benchmarkRule.measureRepeated {
            runBlocking { engine.synthesize(sentence, voiceProfile) }
        }
    }

    @Test
    fun latence_synthese_palier2_sherpa_onnx() {
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

        benchmarkRule.measureRepeated {
            runBlocking { engine.synthesize(sentence, voiceProfile) }
        }
    }
}
