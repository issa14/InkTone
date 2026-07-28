package com.inktone.infrastructure.tts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.TtsEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Vérifie, pour chaque adaptateur `TtsEngine` réel, que la capacité
 * déclarée `wordTimestamps` correspond au comportement observé (§8.4,
 * §8.9) : aucun adaptateur ne prétend une capacité qu'il n'a pas, et
 * aucun n'en fournit une qu'il n'a pas déclarée (jamais de
 * `WordTimestamp` inventé par interpolation, ADR-013).
 */
@RunWith(AndroidJUnit4::class)
class TtsCapabilityConsistencyTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val text = "Bonjour, ceci est un test de coherence des capacites."
    private val sentence = Sentence(index = 0, text = text, startOffset = 0, endOffset = text.length)

    private fun assertCapabilityMatchesBehavior(engine: TtsEngine, voiceProfile: VoiceProfile) = runTest {
        val segment = engine.synthesize(sentence, voiceProfile)
        if (engine.capabilities.wordTimestamps) {
            assertTrue(
                "${engine.id} declare wordTimestamps=true mais n'a produit aucun WordTimestamp",
                segment.wordTimestamps.isNotEmpty(),
            )
        } else {
            assertTrue(
                "${engine.id} declare wordTimestamps=false mais a produit des WordTimestamp - jamais invente par interpolation (ADR-013)",
                segment.wordTimestamps.isEmpty(),
            )
        }
    }

    @Test
    fun palier1_android_natif_respecte_sa_capacite_wordTimestamps() {
        val engine = AndroidNativeTtsEngine(context)
        val voiceProfile = VoiceProfile(id = "vp-native-fr", engine = TtsEngineId.ANDROID_NATIVE, voice = "fr-fr-default", language = "fr-FR")
        assertTrue("Palier 1 declare wordTimestamps=true (Tache 3.1)", engine.capabilities.wordTimestamps)
        assertCapabilityMatchesBehavior(engine, voiceProfile)
    }

    @Test
    fun palier2_sherpa_onnx_respecte_sa_capacite_wordTimestamps() {
        val modelPaths = SherpaOnnxModelPaths(context)
        val staged = File(context.getExternalFilesDir(null), "kokoro-int8-multi-lang-v1_0")
        if (!modelPaths.isReady && staged.exists()) {
            staged.copyRecursively(modelPaths.modelFile.parentFile!!, overwrite = true)
        }
        assumeTrue(
            "Modele vocal Sherpa-ONNX absent - placer manuellement avant ce test (Tache 5.6 le remplacera)",
            modelPaths.isReady,
        )
        val engine = SherpaOnnxTtsEngine(modelPaths)
        val voiceProfile = VoiceProfile(id = "vp-sherpa-fr", engine = TtsEngineId.SHERPA_ONNX, voice = "ff_siwis", language = "fr-FR")
        assertFalse(
            "Palier 2 declare wordTimestamps=false tant que la Tache 5.2 (alignement CTC) n'est pas completee",
            engine.capabilities.wordTimestamps,
        )
        assertCapabilityMatchesBehavior(engine, voiceProfile)
    }
}
