package com.readflow.service.onnx

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.readflow.domain.model.SynthesisResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service d'inférence TTS via Sherpa-ONNX / Kokoro int8 multilingue.
 *
 * Threading : l'instance [OfflineTts] est créée une seule fois (singleton Hilt).
 * La synthèse est appelée depuis [TtsRepositoryImpl] sur [Dispatchers.Default].
 *
 * Modèle : kokoro-int8-multi-lang-v1_0 (110 Mo quantifié, 53 locuteurs, 24 kHz).
 * Phonémisation : KokoroMultiLangLexicon + espeak-ng avec lang="fr" pour le français.
 */
@Singleton
class OnnxInferenceService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OnnxInference"

        // ── Chemins dans assets ──────────────────────
        private const val ASSET_DIR  = "models/kokoro-multi-lang-v1_0"
        private const val ONNX_FILE  = "model.onnx"
        private const val VOICES_BIN = "voices.bin"
        private const val TOKENS_TXT = "tokens.txt"
        private const val LEXICON_EN = "lexicon-us-en.txt"
    }

    // ── Singleton OfflineTts ───────────────────────────────────────
    @Volatile private var tts: OfflineTts? = null

    // ── Voix Kokoro (SIDs documentés par sherpa-onnx) ──────────────
    // 0=af_heart, 1=af_bella, 2=af_nicole, 3=af_sarah, 4=af_sky, 5=am_adam, …
    // Avec lang="fr", espeak-ng phonétise en français donc le résultat
    // est du français avec un accent américain (compréhensible).
    enum class Voice(val sid: Int, val label: String) {
        AF_HEART (0, "af_heart"),
        AF_BELLA (3, "af_bella"),
        AF_NICOLE(6, "af_nicole"),
    }

    // ── API publique ───────────────────────────────────────────────

    /** Initialise le moteur Kokoro UNE SEULE FOIS (idempotent). */
    fun initialize() {
        if (tts != null) return

        val dataDir = copyEspeakDataToInternal()

        // Positional args: model, voices, tokens, dataDir, lexicon, lang, dictDir, lengthScale
        val kokoroConfig = OfflineTtsKokoroModelConfig(
            "$ASSET_DIR/$ONNX_FILE",
            "$ASSET_DIR/$VOICES_BIN",
            "$ASSET_DIR/$TOKENS_TXT",
            dataDir,
            "$ASSET_DIR/$LEXICON_EN",
            "fr",
            "",
            1.0f
        )

        val modelConfig = OfflineTtsModelConfig().apply {
            kokoro = kokoroConfig
            numThreads = 4
            provider = "cpu"
            debug = true
        }
        val config = OfflineTtsConfig(modelConfig, "", "", 1, 1.0f)

        tts = OfflineTts(context.assets, config)
        Log.i(TAG, "Kokoro initialisé — ${tts!!.numSpeakers()} locuteurs, " +
                "${tts!!.sampleRate()} Hz, modèle int8")
    }

    /**
     * Synthèse vocale BLOQUANTE — doit être appelée sur [Dispatchers.Default].
     */
    fun synthesize(
        text: String,
        voice: Voice = Voice.AF_HEART,
        speed: Float = 1.0f
    ): SynthesisResult {
        val engine = tts
            ?: throw IllegalStateException("TTS non initialisé. Appeler initialize() d'abord.")

        val cleaned = text.trim()
        val startMs = System.currentTimeMillis()
        val audio = engine.generate(cleaned, voice.sid, speed.coerceIn(0.5f, 2.0f))
        val elapsedMs = System.currentTimeMillis() - startMs
        val durationMs = ((audio.samples.size.toFloat() / audio.sampleRate) * 1000).toLong()
        val rtf = elapsedMs / durationMs.coerceAtLeast(1).toFloat()

        if (rtf > 3.0f) {
            Log.w(TAG, "RTF élevé: %.2f (\"%s\")".format(rtf, cleaned.take(50)))
        } else {
            Log.i(TAG, "\"${cleaned.take(60)}\" → ${audio.samples.size} éch., " +
                    "${durationMs}ms, RTF=%.2f".format(rtf))
        }
        return SynthesisResult(audio.samples, audio.sampleRate, cleaned,
            voice.label, elapsedMs, durationMs)
    }

    fun release() { tts?.release(); tts = null }

    fun isModelAvailable(): Boolean {
        return try {
            context.assets.open("$ASSET_DIR/$ONNX_FILE").use { true }
        } catch (_: Exception) { false }
    }

    // ── Private ───────────────────────────────────────────────────

    /** Copie espeak-ng-data des assets → stockage interne (une seule fois). */
    private fun copyEspeakDataToInternal(): String {
        val target = File(context.filesDir, "espeak-ng-data")
        if (target.isDirectory && target.listFiles()?.isNotEmpty() == true)
            return target.absolutePath
        Log.i(TAG, "Copie espeak-ng-data → ${target.absolutePath}")
        target.mkdirs()
        copyAssetDir("$ASSET_DIR/espeak-ng-data", target)
        return target.absolutePath
    }

    private fun copyAssetDir(assetPath: String, targetDir: File) {
        context.assets.list(assetPath)?.forEach { child ->
            val childPath = "$assetPath/$child"
            val childFile = File(targetDir, child)
            try {
                context.assets.open(childPath).use { input ->
                    childFile.parentFile?.mkdirs()
                    childFile.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: java.io.FileNotFoundException) {
                copyAssetDir(childPath, File(targetDir, child))
            }
        }
    }
}
