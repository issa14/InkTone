package com.inktone.infrastructure.tts

import android.util.Log
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlaybackEvent
import com.inktone.domain.service.PronunciationRuleApplier
import com.inktone.domain.service.TtsCapabilities
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.service.remapToOriginal
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
 * Palier 2 (ADR-021) — qualité vocale neuronale **VITS Piper
 * `fr_FR-upmc-medium`** (Lot 20 — remplace Kokoro), avec de vrais
 * timestamps mot (alignement forcé CTC, Tâche 5.2 — voir
 * `docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md`).
 *
 * Le modèle porte **2 locuteurs** (vérifié dans `fr_FR-upmc-medium.onnx.json`,
 * pas supposé) : `jessica` (sid 0) et `pierre` (sid 1). Le sid est résolu
 * depuis `VoiceProfile.voice` — jamais une constante en dur.
 *
 * **Latence (Lot 20)** : le legacy a mesuré ce modèle (fp32) sur un
 * Snapdragon 680 réel à **RTF ~0,8** (`legacy/monolith/docs/prototype-report.md`),
 * contre RTF ~4,7 pour Kokoro — le moteur redevient utilisable en temps
 * quasi réel. La mesure int8/fp32 de cette implémentation est consignée
 * par `SherpaOnnxTtsEngineLatencyTest` sur device (budget §11.2
 * « tap → premier audio ≤ 1 500 ms », avec préchauffage si nécessaire).
 *
 * **Prosaïdie** : `lengthScale = 1.08` (voix upmc jugée trop rapide par
 * certains utilisateurs, corrigée — valeur du legacy), `noiseScale =
 * 0.667`, `noiseScaleW = 0.8` (défauts Piper VITS).
 *
 * **Honnêteté `wordTimestamps = true`** : chaque appel réussi à
 * [synthesize] passe par [CtcForcedAligner.align] — jamais un
 * sous-ensemble silencieux (§8.9 : « un moteur ne fait jamais
 * semblant »). Si le modèle CTC n'est pas installé (état transitoire ou
 * échec de son téléchargement), la synthèse reste audible mais renvoie
 * des timestamps **vides** (pas de surlignage, jamais d'interpolation) —
 * dégradation loggée, jamais un crash.
 */
@Singleton
class SherpaOnnxTtsEngine @Inject constructor(
    private val modelPaths: SherpaOnnxModelPaths,
    private val ctcForcedAligner: CtcForcedAligner,
    private val pronunciationRuleApplier: PronunciationRuleApplier,
) : TtsEngine {

    override val id = TtsEngineId.SHERPA_ONNX

    override val capabilities = TtsCapabilities(
        offline = true,
        wordTimestamps = true, // CTC branché (voir KDoc — timestamps vides si modèle absent)
        sentenceTimestamps = true,
        languages = listOf("fr"),
        streamingSynthesis = false,
        speedControl = true,
        pitchControl = false, // VITS n'expose que lengthScale (durée/vitesse), pas de hauteur
        modelSizeMb = 80, // archive fp32 vits-piper-fr_FR-upmc-medium (80,4 Mo) — Lot 20
        license = "Apache-2.0 (sherpa-onnx) + CC-BY-SA-4.0 (voix UPMC upmc-medium) + CC-BY-4.0 (NeMo CTC)",
    )

    // internal (pas private) uniquement pour permettre la mesure de latence
    // decomposée synthese/alignement en test (SherpaOnnxTtsEngineLatencyTest) -
    // jamais accede hors module.
    internal val tts: OfflineTts by lazy {
        check(modelPaths.isReady) { "Modele vocal Sherpa-ONNX absent (${modelPaths.modelFile.parent}) - telechargement non encore effectue" }
        OfflineTts(
            assetManager = null,
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = modelPaths.modelFile.absolutePath,
                        tokens = modelPaths.tokensFile.absolutePath,
                        dataDir = modelPaths.espeakDataDir.absolutePath,
                        lexicon = "", // phonémiseur espeak-ng (pas de lexicon dans l'archive upmc)
                        dictDir = "",
                        // Prosaïdie héritée du legacy (voix upmc « trop rapide » corrigée).
                        noiseScale = PROSODY_NOISE_SCALE,
                        noiseScaleW = PROSODY_NOISE_SCALE_W,
                        lengthScale = PROSODY_LENGTH_SCALE,
                    ),
                    // 4 cœurs performants mesurés sur ce device (Snapdragon
                    // 680 / V2206) — même réglage que Phase 5, conservé.
                    numThreads = 4,
                    provider = "cpu",
                    debug = false,
                ),
                ruleFsts = "", // pas de règle .fst pour ce modèle
                ruleFars = "",
                maxNumSentences = 1,
                silenceScale = 1.0f,
            ),
        )
    }

    override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment =
        withContext(Dispatchers.Default) {
            // Applique AVANT la synthese (Tache 8.3), meme point d'integration
            // que AndroidNativeTtsEngine — reste reversible sans reimport.
            val appliedText = pronunciationRuleApplier.apply(sentence.text)

            val generated = tts.generate(
                text = appliedText.substitutedText,
                sid = sidFor(voiceProfile.voice),
                speed = voiceProfile.speed,
            )

            // Alignement force CTC sur l'audio REELLEMENT produit — le
            // resampling (22050 Hz upmc -> 16 kHz CTC) est fait a
            // l'interieur de CtcForcedAligner.align() a partir du sampleRate
            // reel rapporte par le moteur, jamais suppose. Les WordTimestamp
            // sont ensuite remappes sur sentence.text (texte affiche), jamais
            // sur le texte substitue envoye au moteur.
            val wordTimestamps = if (ctcForcedAligner.isReady) {
                ctcForcedAligner.align(
                    audioSamples = generated.samples,
                    sampleRate = generated.sampleRate,
                    referenceText = appliedText.substitutedText,
                ).map { it.remapToOriginal(appliedText) }
            } else {
                // Degradation honnete et loggee : voix audible, pas de
                // surlignage — jamais d'interpolation (voir KDoc).
                Log.w(TAG, "Modele CTC absent : surlignage mot a mot desactive pour cette phrase")
                emptyList()
            }

            AudioSegment(
                audioData = floatSamplesToPcm16(generated.samples),
                durationMs = (generated.samples.size.toLong() * 1000L) / generated.sampleRate,
                wordTimestamps = wordTimestamps,
                sampleRate = generated.sampleRate,
            )
        }

    /** Résout le sid depuis la voix du profil — défaut `jessica` (sid 0). */
    private fun sidFor(voice: String): Int = when (voice) {
        "pierre" -> SPEAKER_PIERRE_SID
        else -> SPEAKER_JESSICA_SID
    }

    /**
     * Lot 20 — préchauffe le moteur (chargement du modèle ONNX TTS + session
     * d'alignement CTC) hors du premier appel : le premier usage après
     * téléchargement — ou à l'ouverture du Reader dans un process neuf —
     * ne paie pas le chargement froid (~10-20 s mesuré sur V2206), qui
     * dépassait le timeout de synthèse de l'ordonnanceur (budget §11.2
     * « tap → premier audio ≤ 1 500 ms »). Sans effet si le modèle n'est
     * pas prêt (repli voix système assumé, FallbackTtsEngine).
     */
    override fun warmUp() {
        if (!modelPaths.isReady) return
        tts
        ctcForcedAligner.warmUp()
    }

    override fun observePlaybackEvents(): Flow<PlaybackEvent> = callbackFlow { awaitClose { } }

    private companion object {
        const val TAG = "SherpaOnnxTtsEngine"
        // Locuteurs du modèle vits-piper-fr_FR-upmc-medium (verifie dans les
        // metadonnees ONNX, pas devine) : jessica sid 0, pierre sid 1.
        const val SPEAKER_JESSICA_SID = 0
        const val SPEAKER_PIERRE_SID = 1
        // Prosaïdie Piper VITS (defauts) + correction du debit upmc (legacy).
        const val PROSODY_NOISE_SCALE = 0.667f
        const val PROSODY_NOISE_SCALE_W = 0.8f
        const val PROSODY_LENGTH_SCALE = 1.08f
    }
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
