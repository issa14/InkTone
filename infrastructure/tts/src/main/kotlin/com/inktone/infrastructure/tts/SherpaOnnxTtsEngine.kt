package com.inktone.infrastructure.tts

import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlaybackEvent
import com.inktone.domain.service.TtsCapabilities
import com.inktone.domain.service.TtsEngine
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Palier 2 (ADR-021) — qualité vocale neuronale (VITS, voix fr_FR-siwis-
 * medium, CC-BY 4.0), PAS de timestamps mot natifs. Vérifié empiriquement
 * (Tâche 5.1.0, API Kotlin officielle vendorée dans
 * `com.k2fsa.sherpa.onnx.Tts.kt`) : `GeneratedAudio` n'expose que
 * `samples: FloatArray` (normalisé [-1, 1]) et `sampleRate: Int` — jamais
 * de `WordTimestamp`, quel que soit le modèle chargé (VITS ou Kokoro).
 * `wordTimestamps` reste donc `false` ici jusqu'à ce que la Tâche 5.2
 * (alignement forcé CTC) soit réellement complétée et branchée — jamais
 * affirmé vrai par anticipation (§8.9 : "un moteur ne fait jamais
 * semblant").
 *
 * Écart avec ADR-021/le plan de Phase 5 : aucun modèle Kokoro français
 * n'existe dans le catalogue officiel Sherpa-ONNX (vérifié empiriquement,
 * Tâche 5.1.1 — le modèle "multi-lang" ne couvre que zh/en). VITS (poids
 * Piper redistribués par Sherpa-ONNX, exécutés par le moteur VITS
 * indépendant de Sherpa-ONNX — pas le logiciel Piper rejeté en ADR-021)
 * est utilisé à la place, décision Issa.
 */
@Singleton
class SherpaOnnxTtsEngine @Inject constructor(
    private val modelPaths: SherpaOnnxModelPaths,
) : TtsEngine {

    override val id = TtsEngineId.SHERPA_ONNX

    override val capabilities = TtsCapabilities(
        offline = true,
        wordTimestamps = false, // Tache 5.2 (alignement CTC) non complete - voir KDoc
        sentenceTimestamps = true,
        languages = listOf("fr"),
        streamingSynthesis = false,
        speedControl = true,
        pitchControl = false, // VITS (Piper) n'expose pas de controle de hauteur independant de la vitesse, verifie contre l'API OfflineTtsVitsModelConfig (Tache 5.1.0)
        modelSizeMb = 63, // fr_FR-siwis-medium.onnx (~63 Mo) + tokens.txt + espeak-ng-data, mesure reelle Tache 5.1.1
        license = "CC-BY 4.0 (voix siwis, dataset Centre for Speech Technology Voice Cloning Toolkit, University of Edinburgh)",
    )

    private val tts: OfflineTts by lazy {
        check(modelPaths.isReady) { "Modele vocal Sherpa-ONNX absent (${modelPaths.modelFile.parent}) - telechargement non encore cable (Tache 5.6)" }
        OfflineTts(
            assetManager = null,
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = modelPaths.modelFile.absolutePath,
                        tokens = modelPaths.tokensFile.absolutePath,
                        dataDir = modelPaths.espeakDataDir.absolutePath,
                    ),
                    numThreads = 2,
                    provider = "cpu",
                ),
            ),
        )
    }

    override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment =
        withContext(Dispatchers.Default) {
            val generated = tts.generate(text = sentence.text, sid = 0, speed = voiceProfile.speed)
            AudioSegment(
                audioData = floatSamplesToPcm16(generated.samples),
                durationMs = (generated.samples.size.toLong() * 1000L) / generated.sampleRate,
                wordTimestamps = emptyList(), // Tache 5.2
                sampleRate = generated.sampleRate,
            )
        }

    override fun observePlaybackEvents(): Flow<PlaybackEvent> = callbackFlow { awaitClose { } }
}

/**
 * PCM16 signé, little-endian — format attendu par `AudioSegment.audioData`
 * (Tâche 1.7/3.8, `AudioSegmentPlayer`). `GeneratedAudio.samples` est un
 * `FloatArray` normalisé [-1, 1] (API Sherpa-ONNX, Tâche 5.1.0) — jamais
 * du PCM16 brut malgré ce qu'un contrat unique pourrait laisser supposer
 * entre moteurs ; la conversion est donc explicite ici, pas silencieuse.
 */
internal fun floatSamplesToPcm16(samples: FloatArray): ByteArray {
    val out = ByteArray(samples.size * 2)
    for (i in samples.indices) {
        val clamped = samples[i].coerceIn(-1f, 1f)
        val value = (clamped * Short.MAX_VALUE).roundToInt().toShort()
        out[i * 2] = (value.toInt() and 0xFF).toByte()
        out[i * 2 + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }
    return out
}
