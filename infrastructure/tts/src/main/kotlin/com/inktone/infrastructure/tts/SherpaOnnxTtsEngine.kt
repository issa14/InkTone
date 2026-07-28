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
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Palier 2 (ADR-021) — qualité vocale neuronale (Kokoro, voix `ff_siwis`
 * française, Apache-2.0), avec de vrais timestamps mot (alignement forcé
 * CTC, Tâche 5.2, branché ici pour de vrai après avoir été prouvé
 * séparément sur device réel — `docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md`
 * §1-9). `wordTimestamps = true` est honnête : chaque appel réussi à
 * [synthesize] passe systématiquement par [CtcForcedAligner.align] —
 * jamais un sous-ensemble silencieux de phrases avec timestamps et
 * d'autres sans (§8.9 : "un moteur ne fait jamais semblant").
 *
 * Remplace la voix VITS `fr_FR-siwis-medium` retenue en Tâche 5.1.1. Sa
 * prémisse (« aucun modèle Kokoro français n'existe dans le catalogue
 * Sherpa-ONNX, le modèle multi-lang ne couvre que zh/en ») était
 * factuellement fausse — vérifié en pratique le 2026-07-28 : l'app
 * d'exemple officielle `SherpaOnnxTts`, chargée avec
 * `kokoro-int8-multi-lang-v1_0` sur un device Snapdragon 680 réel,
 * confirme une voix française (`ff_siwis`, speaker id 30, lu dans les
 * métadonnées ONNX `speaker2id` du modèle, pas deviné) produisant un
 * français correct (`docs/execution/PROTOTYPE_SYNTHESE_KOKORO_ONNX.md`).
 * `sid = 30` ci-dessous est donc spécifique à ce modèle exact — pas une
 * constante Kokoro générale, à revérifier si le modèle vendoré change.
 *
 * **Alerte performance réelle, mesurée sur device (Snapdragon 680, V2206,
 * 2026-07-28)** : `synthesize()` prend actuellement **~28 à 34 secondes**
 * pour une phrase de ~4,8 s d'audio (dont l'essentiel — ~28-34 s — est la
 * synthèse Kokoro elle-même, l'alignement CTC ~3 s à chaud). Budget §11.2
 * (tap → premier audio ≤ 1 500 ms, silence inter-phrases ≤ 150 ms) **très
 * largement dépassé**, d'un facteur ~20-25×. Détail et pistes :
 * `docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md` §10. Ne pas considérer ce
 * moteur comme viable en production tant que cet écart n'est pas résolu
 * ou explicitement ré-arbitré (Blueprint §11.2 : un dépassement de budget
 * bloque la release ou déclenche un ADR de révision — pas une ignorance
 * silencieuse).
 */
@Singleton
class SherpaOnnxTtsEngine @Inject constructor(
    private val modelPaths: SherpaOnnxModelPaths,
    private val ctcForcedAligner: CtcForcedAligner,
) : TtsEngine {

    override val id = TtsEngineId.SHERPA_ONNX

    override val capabilities = TtsCapabilities(
        offline = true,
        wordTimestamps = true, // Tache 5.2 branchee - voir KDoc
        sentenceTimestamps = true,
        languages = listOf("fr"),
        streamingSynthesis = false,
        speedControl = true,
        pitchControl = false, // OfflineTtsKokoroModelConfig n'expose que lengthScale (duree/vitesse), pas de parametre de hauteur - verifie contre l'API (kotlin-api/Tts.kt)
        modelSizeMb = 290, // Kokoro (164 Mo, voir Tache 5.1.0) + modele CTC int8 + tokens.txt (~126 Mo, Tache 5.2 §7.1)
        license = "Apache-2.0 (Kokoro-82M, hexgrad) + CC-BY-4.0 (NeMo FastConformer CTC multilingue, NVIDIA)",
    )

    // internal (pas private) uniquement pour permettre la mesure de latence
    // decomposee synthese/alignement en test (SherpaOnnxTtsEngineLatencyTest) -
    // jamais accede hors module.
    internal val tts: OfflineTts by lazy {
        check(modelPaths.isReady) { "Modele vocal Sherpa-ONNX absent (${modelPaths.modelFile.parent}) - telechargement non encore cable (Tache 5.6)" }
        OfflineTts(
            assetManager = null,
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = modelPaths.modelFile.absolutePath,
                        voices = modelPaths.voicesFile.absolutePath,
                        tokens = modelPaths.tokensFile.absolutePath,
                        dataDir = modelPaths.espeakDataDir.absolutePath,
                        lexicon = "${modelPaths.lexiconUsEnFile.absolutePath},${modelPaths.lexiconZhFile.absolutePath}",
                        // lang laisse vide : le C++ retombe sur meta_data.voice
                        // (derive du speaker_names[sid] choisi), verifie en
                        // pratique (Tache 5.1.0, app d'exemple) - pas devine.
                    ),
                    numThreads = 2,
                    provider = "cpu",
                ),
                ruleFsts = "${modelPaths.phoneZhFst.absolutePath},${modelPaths.dateZhFst.absolutePath},${modelPaths.numberZhFst.absolutePath}",
            ),
        )
    }

    override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment =
        withContext(Dispatchers.Default) {
            // sid=30 : voix francaise "ff_siwis" de kokoro-int8-multi-lang-v1_0,
            // lue dans les metadonnees ONNX speaker2id du modele (pas devinee) -
            // voir KDoc de la classe.
            val generated = tts.generate(text = sentence.text, sid = FF_SIWIS_SPEAKER_ID, speed = voiceProfile.speed)

            // Alignement force CTC sur l'audio REELLEMENT produit (pas un
            // fichier de test) - resampling 24kHz (Kokoro) -> 16kHz (modele
            // CTC) fait a l'interieur de CtcForcedAligner.align(), a partir
            // du sampleRate reel rapporte par Kokoro, jamais suppose.
            val wordTimestamps = ctcForcedAligner.align(
                audioSamples = generated.samples,
                sampleRate = generated.sampleRate,
                referenceText = sentence.text,
            )

            AudioSegment(
                audioData = floatSamplesToPcm16(generated.samples),
                durationMs = (generated.samples.size.toLong() * 1000L) / generated.sampleRate,
                wordTimestamps = wordTimestamps,
                sampleRate = generated.sampleRate,
            )
        }

    private companion object {
        const val FF_SIWIS_SPEAKER_ID = 30
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
