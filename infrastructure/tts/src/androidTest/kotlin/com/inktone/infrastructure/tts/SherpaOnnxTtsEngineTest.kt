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
 * Valide bout en bout (Tâche 5.1.2/5.2-prod) : sur un device réel,
 * `SherpaOnnxTtsEngine.synthesize()` renvoie un `AudioSegment` réel (audio
 * non vide, sampleRate cohérent avec Kokoro — 24000 Hz) **avec de vrais
 * `WordTimestamp`** produits par l'alignement forcé CTC branché pour de
 * vrai (Tâche 5.2, déjà prouvée séparément —
 * `docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md`).
 *
 * Les deux modèles (Kokoro `~164 Mo` + CTC int8 `~126 Mo`) ne sont PAS
 * committés — même principe que
 * `docs/execution/VALIDATION_EPUB_REEL_LES_MISERABLES.md` (Tâche 4.11)
 * pour l'EPUB réel. Ce test se saute (`assumeTrue`) tant qu'ils ne sont
 * pas placés manuellement — la Tâche 5.6 (téléchargement vérifié par
 * empreinte) remplacera ce geste manuel.
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
     * l'emplacement reel attendu par les *ModelPaths - uniquement pour ce
     * test manuel, jamais un chemin de production (K5 : SAF exclusivement
     * en dehors des tests).
     */
    private fun stageModelFromExternalStorageIfPresent(externalDirName: String, targetDir: File) {
        val staged = File(context.getExternalFilesDir(null), externalDirName)
        if (staged.exists()) {
            staged.copyRecursively(targetDir, overwrite = true)
        }
    }

    @Test
    fun synthesize_produit_un_audioSegment_reel_avec_wordTimestamps_reels() = runTest {
        val modelPaths = SherpaOnnxModelPaths(context)
        val ctcModelPaths = CtcModelPaths(context)
        if (!modelPaths.isReady) {
            stageModelFromExternalStorageIfPresent("kokoro-int8-multi-lang-v1_0", modelPaths.modelFile.parentFile!!)
        }
        if (!ctcModelPaths.isReady) {
            stageModelFromExternalStorageIfPresent("nemo-ctc-fr-multilang-int8", ctcModelPaths.modelFile.parentFile!!)
        }
        assumeTrue(
            "Modele vocal Sherpa-ONNX absent (${modelPaths.modelFile.parent}) - placer manuellement avant ce test (Tache 5.6 le remplacera)",
            modelPaths.isReady,
        )
        assumeTrue(
            "Modele d'alignement CTC absent (${ctcModelPaths.modelFile.parent}) - placer manuellement avant ce test (Tache 5.6 le remplacera)",
            ctcModelPaths.isReady,
        )

        val engine = SherpaOnnxTtsEngine(modelPaths, CtcForcedAligner(ctcModelPaths))
        val text = "Bonjour le monde. Ceci est un test pour vérifier l'alignement."
        val sentence = Sentence(index = 0, text = text, startOffset = 0, endOffset = text.length)

        val segment = engine.synthesize(sentence, voiceProfile())

        assertTrue("audioData ne doit pas etre vide", segment.audioData.isNotEmpty())
        assertTrue("durationMs doit etre positif", segment.durationMs > 0)
        assertEquals("sampleRate attendu pour Kokoro (confirme en pratique, Tache 5.1.0)", 24000, segment.sampleRate)
        assertTrue(
            "Tache 5.2 (alignement CTC) branchee - des WordTimestamp reels sont attendus, jamais simules",
            segment.wordTimestamps.isNotEmpty(),
        )
        assertTrue(
            "chaque WordTimestamp doit pointer vers une position reelle dans le texte source",
            segment.wordTimestamps.all { it.charOffset in text.indices },
        )
        assertTrue(
            "les timestamps doivent etre ordonnes et dans la duree du segment",
            segment.wordTimestamps.zipWithNext().all { (a, b) -> a.startMs <= b.startMs } &&
                segment.wordTimestamps.all { it.endMs <= segment.durationMs },
        )

        // audioData est du PCM16 (2 octets/echantillon) : la duree
        // annoncee doit correspondre au volume d'octets reellement produit.
        val expectedDurationMs = (segment.audioData.size / 2).toLong() * 1000L / segment.sampleRate
        assertEquals("durationMs coherent avec audioData/sampleRate", expectedDurationMs, segment.durationMs)
    }
}
