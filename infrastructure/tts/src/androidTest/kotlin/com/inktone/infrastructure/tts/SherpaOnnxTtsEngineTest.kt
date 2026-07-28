package com.inktone.infrastructure.tts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Valide bout en bout (Tâche 5.1.2) : sur un device réel,
 * `SherpaOnnxTtsEngine.synthesize()` renvoie un `AudioSegment` réel
 * (audio non vide, sampleRate cohérent avec Kokoro — 24000 Hz, confirmé
 * en pratique via l'app d'exemple officielle sur device réel, Tâche
 * 5.1.0), sans `WordTimestamp` (Tâche 5.2 non branchée ici).
 *
 * Le modèle vocal (`kokoro-int8-multi-lang-v1_0`, ~164 Mo, licence
 * Apache-2.0) n'est PAS committé — pas un fixture de test automatisé
 * pour l'instant, même principe que
 * `docs/execution/VALIDATION_EPUB_REEL_LES_MISERABLES.md` (Tâche 4.11)
 * pour l'EPUB réel. Ce test se saute (`assumeTrue`) tant que le modèle
 * n'est pas placé manuellement dans
 * `context.filesDir/voices/kokoro-int8-multi-lang-v1_0/` — la Tâche 5.6
 * (téléchargement vérifié par empreinte) remplacera ce geste manuel.
 */
@RunWith(AndroidJUnit4::class)
class SherpaOnnxTtsEngineTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun voiceProfile() = VoiceProfile(
        id = "vp-sherpa-fr",
        engine = TtsEngineId.SHERPA_ONNX,
        voice = "ff_siwis",
        language = "fr-FR",
    )

    /**
     * Copie ponctuelle depuis le stockage externe app-specifique (accessible
     * par `adb push` sans `run-as`, contrairement au stockage prive) vers
     * l'emplacement reel attendu par `SherpaOnnxModelPaths` - uniquement
     * pour ce test manuel, jamais un chemin de production (K5 : SAF
     * exclusivement en dehors des tests).
     */
    private fun stageModelFromExternalStorageIfPresent(modelPaths: SherpaOnnxModelPaths) {
        if (modelPaths.isReady) return
        val staged = File(context.getExternalFilesDir(null), "kokoro-int8-multi-lang-v1_0")
        if (staged.exists()) {
            staged.copyRecursively(modelPaths.modelFile.parentFile!!, overwrite = true)
        }
    }

    @Test
    fun synthesize_produit_un_audioSegment_reel_sans_wordTimestamps() = runTest {
        val modelPaths = SherpaOnnxModelPaths(context)
        stageModelFromExternalStorageIfPresent(modelPaths)
        assumeTrue(
            "Modele vocal Sherpa-ONNX absent (${modelPaths.modelFile.parent}) - placer manuellement avant ce test (Tache 5.6 le remplacera)",
            modelPaths.isReady,
        )

        val engine = SherpaOnnxTtsEngine(modelPaths)
        val text = "— Bonjour, dit-elle, êtes-vous l'homme qui peut-être m'attendait ?"
        val sentence = Sentence(index = 0, text = text, startOffset = 0, endOffset = text.length)

        val segment = engine.synthesize(sentence, voiceProfile())

        assertTrue("audioData ne doit pas etre vide", segment.audioData.isNotEmpty())
        assertTrue("durationMs doit etre positif", segment.durationMs > 0)
        assertEquals("sampleRate attendu pour Kokoro (confirme en pratique, Tache 5.1.0)", 24000, segment.sampleRate)
        assertEquals(
            "aucun WordTimestamp - Tache 5.2 (alignement CTC) non completee, jamais simule",
            emptyList<Any>(),
            segment.wordTimestamps,
        )

        // audioData est du PCM16 (2 octets/echantillon) : la duree
        // annoncee doit correspondre au volume d'octets reellement produit.
        val expectedDurationMs = (segment.audioData.size / 2).toLong() * 1000L / segment.sampleRate
        assertEquals("durationMs coherent avec audioData/sampleRate", expectedDurationMs, segment.durationMs)
    }
}
