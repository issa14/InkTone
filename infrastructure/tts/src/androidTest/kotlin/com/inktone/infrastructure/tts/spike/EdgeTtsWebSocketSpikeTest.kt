package com.inktone.infrastructure.tts.spike

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.floor

/**
 * SPIKE — Lot 14, Palier 1, Tâche 1.2 (round-trip) et 1.3 (word boundaries).
 * Instrument de mesure, pas un test d'assertion classique : il prouve ou
 * infirme sur device réel le protocole WebSocket Microsoft Edge TTS
 * (`speech.platform.bing.com`), référencé par le legacy
 * (`legacy/monolith` → `service/edge/EdgeTtsClient.kt`). Le verdict remplit
 * `docs/execution/SPIKE_EDGE_TTS_WEBSOCKET.md` §6-7 et fige la valeur de
 * `TtsCapabilities.wordTimestamps` pour le Palier 3 — aucune valeur supposée.
 */
@RunWith(AndroidJUnit4::class)
class EdgeTtsWebSocketSpikeTest {

    private companion object {
        const val TAG = "EdgeTtsSpike"
        const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        const val SEC_MS_GEC_VERSION = "1-143.0.3650.75"
        const val WS_BASE =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
                "?TrustedClientToken=$TRUSTED_CLIENT_TOKEN"
        const val TEST_SENTENCE = "Bonjour, ceci est une phrase de test."
        const val VOICE = "fr-FR-VivienneNeural"
        const val TIMEOUT_SECONDS = 20L
    }

    /**
     * Partie A — round-trip minimal : wordBoundaryEnabled = false, pour
     * isoler la variable. Prouve que la chaîne WebSocket → MP3 → PCM
     * fonctionne de bout en bout.
     */
    @Test
    fun partieA_roundTrip_synthese_phrase_test() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = runSynthesis(wordBoundaryEnabled = false)
        Log.i(TAG, "PARTIE_A | chunks=${result.chunkCount}, mp3Bytes=${result.mp3Bytes.size}")
        Log.i(TAG, "PARTIE_A | wordBoundaryEvents=${result.wordBoundaryEvents}")

        // Décodage MP3 → PCM (pipeline minimal, pas factorisé en production)
        val pcm = decodeMp3ToPcm(result.mp3Bytes, context.cacheDir)
        val durationMs = (pcm.samples.size.toDouble() / 2.0 / pcm.sampleRate) * 1000.0
        Log.i(TAG, "PARTIE_A | pcmSamples=${pcm.samples.size}, sampleRate=${pcm.sampleRate}, " +
            "durationMs=${durationMs.toLong()}")
        Log.i(TAG, "PARTIE_A | ROUND_TRIP_OK=${result.mp3Bytes.isNotEmpty() && pcm.samples.isNotEmpty()}")
    }

    /**
     * Partie B — word boundaries : wordBoundaryEnabled = true. Capture les
     * trames `Path:wordboundary` et vérifie leur contenu (Offset/Duration).
     * C'est la variable que le legacy n'activait pas, et que ce spike teste.
     */
    @Test
    fun partieB_wordBoundaries_sont_extractibles() {
        val result = runSynthesis(wordBoundaryEnabled = true)
        Log.i(TAG, "PARTIE_B | chunks=${result.chunkCount}, mp3Bytes=${result.mp3Bytes.size}")
        Log.i(TAG, "PARTIE_B | wordBoundaryEvents=${result.wordBoundaryEvents}")
        result.wordBoundaryEvents.forEach { Log.i(TAG, "PARTIE_B | wordboundary brut: $it") }
        Log.i(TAG, "PARTIE_B | WORD_BOUNDARIES_OK=${result.wordBoundaryEvents.isNotEmpty()}")
    }

    // ── Moteur du spike ──────────────────────────────────────────────────

    private data class SynthesisCapture(
        val mp3Bytes: ByteArray,
        val chunkCount: Int,
        val wordBoundaryEvents: List<String>,
    )

    /**
     * Ouvre le WebSocket, envoie config + SSML, collecte les chunks MP3 et
     * les trames wordboundary éventuelles, puis ferme sur `Path:turn.end`.
     */
    private fun runSynthesis(wordBoundaryEnabled: Boolean): SynthesisCapture {
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val connectId = UUID.randomUUID().toString().replace("-", "")
        val wsUrl = "$WS_BASE&ConnectionId=$connectId" +
            "&Sec-MS-GEC=${generateSecMsGec()}" +
            "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

        val audioChunks = ByteArrayOutputStream()
        val chunkCount = AtomicInteger(0)
        val wordBoundaryEvents = mutableListOf<String>()
        val doneLatch = CountDownLatch(1)
        var failure: Throwable? = null

        val request = Request.Builder()
            .url(wsUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
            .header("Accept-Encoding", "gzip, deflate, br, zstd")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Sec-WebSocket-Version", "13")
            .header("X-Speech-API-Audio-Format", "audio-24khz-48kbitrate-mono-mp3")
            .header("Cookie", "muid=${generateMuid()}")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket ouvert (${response.code}) — envoi config + SSML")
                webSocket.send(
                    "X-RequestId:${UUID.randomUUID().toString().replace("-", "")}\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        buildConfigBody(wordBoundaryEnabled),
                )
                webSocket.send(
                    "X-RequestId:${UUID.randomUUID().toString().replace("-", "")}\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "Path:ssml\r\n\r\n" +
                        buildSsml(TEST_SENTENCE),
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Découverte empirique du spike : avec wordBoundaryEnabled=true,
                // les frontières de mot arrivent sous `Path:audio.metadata`
                // (le legacy supposait `Path:wordboundary`, jamais reçu).
                // Capture du corps COMPLET (pas tronqué) pour preuve.
                if (text.contains("Path:audio.metadata") || text.contains("Path:wordboundary")) {
                    wordBoundaryEvents.add(text)
                    Log.i(TAG, "METADATA | ${text.take(600)}")
                } else if (text.contains("Path:turn.end")) {
                    Log.i(TAG, "turn.end reçu — chunks=$chunkCount, octets=${audioChunks.size()}")
                    webSocket.close(1000, "OK")
                    doneLatch.countDown()
                } else if (text.contains("Path:audio")) {
                    Log.i(TAG, "Path:audio reçu (début flux MP3)")
                } else {
                    Log.i(TAG, "onMessage(text): ${text.take(120)}")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                chunkCount.incrementAndGet()
                audioChunks.write(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message} (response=$response)", t)
                failure = t
                doneLatch.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket fermé (code=$code, reason=$reason)")
                doneLatch.countDown()
            }
        }

        val webSocket = httpClient.newWebSocket(request, listener)
        val completed = doneLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        webSocket.cancel()

        if (!completed) {
            Log.e(TAG, "TIMEOUT après $TIMEOUT_SECONDS s — aucun turn.end reçu")
        }
        failure?.let { Log.e(TAG, "Échec spike: ${it.message}", it) }

        return SynthesisCapture(
            mp3Bytes = audioChunks.toByteArray(),
            chunkCount = chunkCount.get(),
            wordBoundaryEvents = wordBoundaryEvents.toList(),
        )
    }

    private fun buildConfigBody(wordBoundaryEnabled: Boolean): String {
        val wordBoundary = if (wordBoundaryEnabled) "true" else "false"
        return "{\"context\":{\"system\":{\"name\":\"SpeechSDK\",\"version\":\"1.19.0\"," +
            "\"build\":\"20220101\",\"lang\":\"fr-FR\"},\"os\":{\"platform\":\"Android\"," +
            "\"name\":\"Android\"},\"synthesis\":{\"audio\":{\"metadataoptions\":{" +
            "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"$wordBoundary\"}," +
            "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
    }

    private fun buildSsml(text: String): String {
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
        return "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" " +
            "xmlns:mstts=\"http://www.w3.org/2001/mstts\" xml:lang=\"fr-FR\">" +
            "<voice name=\"$VOICE\"><prosody rate=\"+0%\" pitch=\"+0Hz\">$escaped</prosody>" +
            "</voice></speak>"
    }

    private fun generateSecMsGec(): String {
        val nowSeconds = System.currentTimeMillis() / 1000.0
        val winSeconds = nowSeconds + 11_644_473_600L
        val rounded = floor(winSeconds / 300.0) * 300.0
        val ticks = (rounded * 10_000_000).toLong()
        val input = "${ticks}${TRUSTED_CLIENT_TOKEN}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { "%02x".format(it) }.uppercase()
    }

    private fun generateMuid(): String {
        val bytes = ByteArray(16)
        for (i in bytes.indices) {
            bytes[i] = ((System.nanoTime() shr (i * 4)) and 0xFF).toByte()
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.take(16).joinToString("") { "%02x".format(it) }.uppercase()
    }

    // ── Décodage MP3 → PCM (pipeline minimal de spike) ───────────────────

    private data class PcmAudio(val samples: ShortArray, val sampleRate: Int)

    private fun decodeMp3ToPcm(mp3Bytes: ByteArray, cacheDir: File): PcmAudio {
        check(mp3Bytes.isNotEmpty()) { "Flux MP3 vide — rien à décoder" }
        val tempFile = File.createTempFile("edge_spike_", ".mp3", cacheDir)
        try {
            FileOutputStream(tempFile).use { it.write(mp3Bytes) }

            val extractor = MediaExtractor()
            extractor.setDataSource(tempFile.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalStateException("Aucune piste audio dans le flux MP3")

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("MIME introuvable")
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            extractor.selectTrack(trackIndex)

            val bufferInfo = MediaCodec.BufferInfo()
            val output = mutableListOf<Short>()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outputIndex >= 0 -> {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                        if (bufferInfo.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                            val shortCount = bufferInfo.size / 2
                            val shortBuf = ShortArray(shortCount)
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.asShortBuffer().get(shortBuf)
                            output.addAll(shortBuf.toList())
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            return PcmAudio(samples = output.toShortArray(), sampleRate = sampleRate)
        } finally {
            tempFile.delete()
        }
    }
}
