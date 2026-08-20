package com.inktone.infrastructure.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.inktone.domain.service.WordTimestamp
import java.nio.FloatBuffer
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alignement forcé CTC (Tâche 5.2) — produit de vrais [WordTimestamp] à
 * partir de l'audio réellement synthétisé et du texte de la phrase, pas
 * une simulation. Pipeline déjà prouvé sur device physique avant ce
 * portage (`docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md` §1-9) :
 * resampling (Tâche 3.8.0/[AudioResampler]) → features fbank ([CtcFbankNative],
 * JNI/kaldi-native-fbank) → inférence ONNX (modèle NeMo CTC int8,
 * `onnxruntime-android` — même version 1.27.0 que le `.so` déjà vendoré
 * par sherpa-onnx, un seul chargé, voir `build.gradle.kts`) → Viterbi
 * contraint ([viterbiForcedAlignment]) → correspondance mot ↔ position
 * dans le texte source.
 *
 * Modèle entraîné sur du **16 kHz** (`CTC_SAMPLE_RATE`) — jamais supposé
 * égal au taux de sortie du moteur TTS appelant (Kokoro produit du
 * 24 kHz) : le [AudioResampler] est toujours invoqué avec le taux réel
 * fourni par l'appelant, jamais une constante figée des deux côtés.
 */
@Singleton
class CtcForcedAligner @Inject constructor(
    private val modelPaths: CtcModelPaths,
) {

    val isReady: Boolean get() = modelPaths.isReady

    private val tokenTable: TokenTable by lazy {
        loadTokens(modelPaths.tokensFile.readLines())
    }

    private val session: OrtSession by lazy {
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        // Lot 20 — l'alignement CTC est le goulot de latence de la synthèse
        // neuronale (mesuré sur V2206 : ~1,6 s par phrase à chaud, ~4,6 s à
        // froid pour ~3,7 s d'audio). setIntraOpNumThreads(4) est appliqué
        // (même réglage que le moteur TTS) mais **sans effet mesuré** sur ce
        // modèle/device : le FastConformer est majoritairement séquentiel.
        // Le coût résiduel est consigné dans LOT_20_RESTAURATION_SHERPA_UPMC.md.
        options.setIntraOpNumThreads(ALIGNER_NUM_THREADS)
        env.createSession(modelPaths.modelFile.absolutePath, options)
    }

    /** Lot 20 — initialise la session d'alignement (chargement du modèle
     * ONNX) hors du premier appel : le préchauffage fait disparaître le
     * chargement froid du premier surlignage. */
    internal fun warmUp() {
        session
    }

    /**
     * @param audioSamples PCM flottant normalisé [-1, 1], au taux [sampleRate] (PAS forcément 16 kHz).
     * @param referenceText texte exact de la phrase synthétisée (sert de référence à l'alignement forcé).
     */
    fun align(audioSamples: FloatArray, sampleRate: Int, referenceText: String): List<WordTimestamp> {
        check(isReady) { "Modele d'alignement CTC absent (${modelPaths.modelFile.parent}) - telechargement non encore cable (Tache 5.6)" }

        val resampled = AudioResampler.resample(audioSamples, sampleRate, CTC_SAMPLE_RATE)
        val feats = CtcFbankNative.computeNemoFbank(resampled, CTC_SAMPLE_RATE)
        val numFrames = feats.size / FBANK_DIM
        if (numFrames == 0) return emptyList()

        val logProbs = runInference(feats, numFrames)

        val refIds = textToTokenIds(referenceText, tokenTable)
        if (refIds.isEmpty()) return emptyList()

        val segments = viterbiForcedAlignment(logProbs, refIds, tokenTable.blankId, FRAME_SHIFT_S)
        val alignedWords = wordsFromViterbiSegments(segments, tokenTable)

        return mapToWordTimestamps(alignedWords, referenceText)
    }

    private fun runInference(feats: FloatArray, numFrames: Int): Array<FloatArray> {
        val env = OrtEnvironment.getEnvironment()

        // audio_signal: (1, 80, T) feature-major - deja verifie contre
        // session.get_inputs() cote Python, pas devine (rapport §5.1).
        val audioBuf = FloatBuffer.allocate(FBANK_DIM * numFrames)
        for (d in 0 until FBANK_DIM) {
            for (t in 0 until numFrames) {
                audioBuf.put(feats[t * FBANK_DIM + d])
            }
        }
        audioBuf.rewind()
        val audioTensor = OnnxTensor.createTensor(env, audioBuf, longArrayOf(1, FBANK_DIM.toLong(), numFrames.toLong()))

        val lengthBuf = LongBuffer.allocate(1).put(numFrames.toLong())
        lengthBuf.rewind()
        val lengthTensor = OnnxTensor.createTensor(env, lengthBuf, longArrayOf(1))

        return audioTensor.use { at ->
            lengthTensor.use { lt ->
                session.run(mapOf("audio_signal" to at, "length" to lt)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    (result.get(0).value as Array<Array<FloatArray>>)[0]
                }
            }
        }
    }

    /**
     * Fait correspondre chaque mot aligné (issu du texte normalisé - minuscules,
     * ponctuation retiree) a sa position reelle (charOffset) dans le texte
     * source de la phrase - recherche insensible a la casse, sequentielle
     * (avance systematiquement apres chaque mot trouve) pour gerer les mots
     * repetes sans les confondre entre eux.
     */
    private fun mapToWordTimestamps(words: List<AlignedWord>, sourceText: String): List<WordTimestamp> {
        val result = mutableListOf<WordTimestamp>()
        var searchFrom = 0
        for (w in words) {
            val idx = sourceText.indexOf(w.word, startIndex = searchFrom, ignoreCase = true)
            if (idx == -1) continue // mot non retrouve tel quel dans le texte source - ignore plutot qu'invente
            result.add(
                WordTimestamp(
                    word = w.word,
                    startMs = (w.startS * 1000).toLong(),
                    endMs = (w.endS * 1000).toLong(),
                    charOffset = idx,
                ),
            )
            searchFrom = idx + w.word.length
        }
        return result
    }

    private companion object {
        const val CTC_SAMPLE_RATE = 16000
        const val FBANK_DIM = 80
        const val SUBSAMPLING_FACTOR = 8
        const val FRAME_SHIFT_S = 0.010 * SUBSAMPLING_FACTOR
        // Lot 20 — mêmes threads que le moteur TTS (optimal mesuré sur V2206).
        const val ALIGNER_NUM_THREADS = 4
    }
}
