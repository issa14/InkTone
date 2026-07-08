package com.readflow.service.onnx

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.readflow.domain.model.SynthesisResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnnxInferenceService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OnnxInference"
        private const val ASSET_DIR = "models/vits-piper-fr_FR-upmc-medium"
        private const val ONNX_FILE = "fr_FR-upmc-medium.onnx"
    }

    private var tts: OfflineTts? = null

    enum class Voice(val sid: Int, val label: String) {
        JESSICA(0, "Jessica"),
        PIERRE(1, "Pierre")
    }

    fun initialize() {
        if (tts != null) return

        val dataDir = copyEspeakDataToInternal()

        val vitsConfig = OfflineTtsVitsModelConfig(
            model = "$ASSET_DIR/$ONNX_FILE",
            lexicon = "",
            tokens = "$ASSET_DIR/tokens.txt",
            dataDir = dataDir,
            dictDir = "",
            noiseScale = 0.667f,
            noiseScaleW = 0.8f,
            lengthScale = 1.0f
        )

        val modelConfig = OfflineTtsModelConfig().apply { vits = vitsConfig }
        val config = OfflineTtsConfig(modelConfig, "", "", 1, 1.0f)

        tts = OfflineTts(context.assets, config)
        Log.i(TAG, "OK — ${tts!!.numSpeakers()} locuteurs, ${tts!!.sampleRate()} Hz")
    }

    fun synthesize(text: String, voice: Voice = Voice.JESSICA, speed: Float = 1.0f): SynthesisResult {
        val engine = tts ?: throw IllegalStateException("Non initialisé.")
        val startMs = System.currentTimeMillis()
        val audio = engine.generate(text.trim(), voice.sid, speed.coerceIn(0.5f, 2.0f))
        val elapsedMs = System.currentTimeMillis() - startMs
        val durationMs = ((audio.samples.size.toFloat() / audio.sampleRate) * 1000).toLong()
        Log.i(TAG, "\"${text.take(60)}\" → ${audio.samples.size} éch., " +
                "${durationMs}ms, RTF=${"%.2f".format(elapsedMs / durationMs.coerceAtLeast(1).toFloat())}")
        return SynthesisResult(audio.samples, audio.sampleRate, text, voice.label, elapsedMs, durationMs)
    }

    fun release() { tts?.release(); tts = null }

    private fun copyEspeakDataToInternal(): String {
        val target = File(context.filesDir, "espeak-ng-data")
        if (target.exists() && target.isDirectory && target.listFiles()?.isNotEmpty() == true)
            return target.absolutePath
        Log.i(TAG, "Copie espeak-ng-data depuis les assets...")
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
            } catch (_: Exception) {
                copyAssetDir(childPath, File(targetDir, child))
            }
        }
    }
}
