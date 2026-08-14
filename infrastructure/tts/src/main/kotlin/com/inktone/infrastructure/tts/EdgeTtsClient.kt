package com.inktone.infrastructure.tts

import android.util.Log
import com.inktone.infrastructure.tts.di.EdgeTts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

/**
 * Frontière de mot renvoyée par Edge TTS via les trames `Path:audio.metadata`
 * (protocole vérifié sur device — spike Lot 14 Palier 1). `offsetTicks` et
 * `durationTicks` sont en **ticks 100 ns** (époque FILETIME) :
 * `ms = ticks / 10_000`.
 */
data class EdgeWordBoundary(
    val offsetTicks: Long,
    val durationTicks: Long,
    val text: String,
)

/**
 * Résultat brut d'une synthèse Edge TTS : le flux MP3 concaténé et les
 * frontières de mot éventuelles. Classe ordinaire (pas `data class`) car
 * `mp3Bytes` est un `ByteArray` — l'égalité par défaut comparerait des
 * références (même piège documenté pour `AudioSegment`).
 */
class EdgeTtsResult(
    val mp3Bytes: ByteArray,
    val wordBoundaries: List<EdgeWordBoundary>,
)

/**
 * Client WebSocket du service Microsoft Edge TTS (gratuit, cloud, API non
 * officielle — ADR-024). Protocole vérifié sur device réel (spike
 * `EdgeTtsWebSocketSpikeTest`) : connexion `speech.platform.bing.com`, trame
 * `speech.config` puis SSML, chunks binaires MP3, frontières de mot sous
 * `Path:audio.metadata` (pas `Path:wordboundary` — supposition du legacy,
 * infirmée), fin sur `Path:turn.end`.
 *
 * Retry : 3 tentatives, backoff exponentiel (500 ms → 1000 ms), sur erreurs
 * **transitoires** uniquement (réseau + timeout). Les erreurs permanentes
 * (handshake rejeté, 403) remontent immédiatement, sans retry (ADR-024,
 * décision 5). Le décodage MP3 est la responsabilité de [Mp3Decoder], pas
 * de ce client (séparation testable).
 */
@Singleton
class EdgeTtsClient @Inject constructor(
    @EdgeTts private val httpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "EdgeTtsClient"

        /** Token de confiance pour l'API Edge TTS (non officielle, public
         *  dans le projet Python `edge-tts`) — constante technique, pas un
         *  secret (ADR-024, décision 4). */
        const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        const val SEC_MS_GEC_VERSION = "1-143.0.3650.75"
        const val WS_BASE =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
                "?TrustedClientToken=$TRUSTED_CLIENT_TOKEN"

        private const val SYNTHESIS_TIMEOUT_MS = 15_000L
        private const val MAX_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 500L
        private const val WIN_EPOCH_SECONDS = 11_644_473_600L

        val VOICES = listOf("fr-FR-VivienneNeural", "fr-FR-HenriNeural")
        const val DEFAULT_VOICE = "fr-FR-VivienneNeural"

        /** SHA256("{ticks}{token}") hex uppercase — algorithme edge-tts v7.2.8. */
        fun generateSecMsGec(): String {
            val nowSeconds = System.currentTimeMillis() / 1000.0
            val winSeconds = nowSeconds + WIN_EPOCH_SECONDS
            val rounded = floor(winSeconds / 300.0) * 300.0
            return secMsGecForTicks((rounded * 10_000_000).toLong())
        }

        /** Fonction pure : SHA256("{ticks}{token}") uppercase. `internal` pour test déterministe (K13). */
        internal fun secMsGecForTicks(ticks: Long): String {
            val input = "${ticks}${TRUSTED_CLIENT_TOKEN}"
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.US_ASCII))
            return digest.joinToString("") { "%02x".format(it) }.uppercase()
        }

        fun generateMuid(): String {
            val bytes = ByteArray(16)
            for (i in bytes.indices) {
                bytes[i] = ((System.nanoTime() shr (i * 4)) and 0xFF).toByte()
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.take(16).joinToString("") { "%02x".format(it) }.uppercase()
        }

        /** SSML avec échappement XML et taux de vitesse dérivé de `speed`. */
        fun buildSsml(text: String, voiceName: String, speed: Float): String {
            val escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
            val ratePercent = ((speed - 1.0f) * 100).toInt()
            val rateStr = if (ratePercent >= 0) "+$ratePercent%" else "$ratePercent%"
            return "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" " +
                "xmlns:mstts=\"http://www.w3.org/2001/mstts\" xml:lang=\"fr-FR\">" +
                "<voice name=\"$voiceName\"><prosody rate=\"$rateStr\" pitch=\"+0Hz\">" +
                "$escaped</prosody></voice></speak>"
        }

        /** Trame `speech.config` — `wordBoundaryEnabled: true` activé (spike prouvé). */
        fun buildConfigBody(wordBoundaryEnabled: Boolean = true): String {
            val wordBoundary = if (wordBoundaryEnabled) "true" else "false"
            return "{\"context\":{\"system\":{\"name\":\"SpeechSDK\",\"version\":\"1.19.0\"," +
                "\"build\":\"20220101\",\"lang\":\"fr-FR\"},\"os\":{\"platform\":\"Android\"," +
                "\"name\":\"Android\"},\"synthesis\":{\"audio\":{\"metadataoptions\":{" +
                "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"$wordBoundary\"}," +
                "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
        }

        /** Classification par **type** d'exception, jamais par message (legacy). */
        fun isNetworkError(e: Throwable): Boolean {
            return e is UnknownHostException ||
                e is SocketTimeoutException ||
                e is ConnectException ||
                e is SocketException
        }
    }

    /**
     * Synthétise un texte via Edge TTS avec retry automatique. Renvoie le
     * flux MP3 brut et les frontières de mot (éventuellement vides).
     *
     * @param voiceName une des voix de [VOICES] ; toute autre valeur retombe
     *   sur [DEFAULT_VOICE] (jamais une voix invalide envoyée au serveur).
     */
    suspend fun synthesize(text: String, voiceName: String, speed: Float): EdgeTtsResult =
        synthesizeAt(WS_BASE, text, voiceName, speed)

    /** Surcharge interne (base URL configurable) — utilisée par les tests MockWebServer. */
    internal suspend fun synthesizeAt(
        baseUrl: String,
        text: String,
        voiceName: String,
        speed: Float,
    ): EdgeTtsResult = withContext(Dispatchers.IO) {
        val voice = if (voiceName in VOICES) voiceName else DEFAULT_VOICE
        val ssml = buildSsml(text, voice, speed.coerceIn(0.5f, 2.0f))

        var lastException: Exception? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
                if (attempt > 0) {
                    val delayMs = RETRY_BASE_DELAY_MS * (1L shl (attempt - 1))
                    Log.w(TAG, "Retry ${attempt + 1}/$MAX_RETRIES après ${delayMs}ms")
                    delay(delayMs)
                }
                return@withContext withTimeout(SYNTHESIS_TIMEOUT_MS) {
                    synthesizeViaWebSocket(baseUrl, ssml)
                }
            } catch (e: TimeoutCancellationException) {
                // Timeout = transitoire → retry (sauf dernière tentative).
                lastException = e
                if (attempt == MAX_RETRIES - 1) throw e
                Log.w(TAG, "Timeout synthèse (tentative ${attempt + 1}/$MAX_RETRIES)")
            } catch (e: CancellationException) {
                throw e // annulation réelle : jamais retryée ni avalée
            } catch (e: Exception) {
                lastException = e
                if (!isNetworkError(e) || attempt == MAX_RETRIES - 1) throw e
                Log.w(TAG, "Tentative ${attempt + 1}/$MAX_RETRIES échouée (transitoire): ${e.message}")
            }
        }
        throw lastException ?: IllegalStateException("Échec Edge TTS inattendu")
    }

    private suspend fun synthesizeViaWebSocket(baseUrl: String, ssml: String): EdgeTtsResult {
        val deferred = CompletableDeferred<EdgeTtsResult>()
        val connectId = UUID.randomUUID().toString().replace("-", "")
        val wsUrl = "$baseUrl&ConnectionId=$connectId" +
            "&Sec-MS-GEC=${generateSecMsGec()}" +
            "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

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
            private val audioChunks = ByteArrayOutputStream()
            private val boundaries = mutableListOf<EdgeWordBoundary>()

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket ouvert (${response.code}) — envoi config + SSML")
                webSocket.send(
                    "X-RequestId:${UUID.randomUUID().toString().replace("-", "")}\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        buildConfigBody(wordBoundaryEnabled = true),
                )
                webSocket.send(
                    "X-RequestId:${UUID.randomUUID().toString().replace("-", "")}\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "Path:ssml\r\n\r\n" +
                        ssml,
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                when {
                    text.contains("Path:audio.metadata") || text.contains("Path:wordboundary") -> {
                        boundaries.addAll(parseWordBoundaries(text))
                    }
                    text.contains("Path:turn.end") -> {
                        Log.d(TAG, "turn.end — ${audioChunks.size()} octets, ${boundaries.size} frontières")
                        webSocket.close(1000, "OK")
                        deferred.complete(EdgeTtsResult(audioChunks.toByteArray(), boundaries.toList()))
                    }
                    text.contains("Path:audio") -> Log.d(TAG, "Path:audio (début flux MP3)")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                audioChunks.write(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message} (response=$response)", t)
                deferred.completeExceptionally(
                    IllegalStateException("Edge TTS : échec WebSocket — ${t.message}", t),
                )
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket fermé (code=$code, reason=$reason)")
                if (!deferred.isCompleted) {
                    deferred.completeExceptionally(
                        IllegalStateException("Edge TTS : WebSocket fermé inopinément (code=$code)"),
                    )
                }
            }
        }

        val webSocket = httpClient.newWebSocket(request, listener)
        return try {
            deferred.await()
        } finally {
            if (!deferred.isCompleted) {
                webSocket.close(1000, "Cancelled")
            }
        }
    }

    /** Extrait et parse le corps JSON d'une trame `Path:audio.metadata`. */
    private fun parseWordBoundaries(frame: String): List<EdgeWordBoundary> {
        return try {
            val jsonStart = frame.indexOf('{')
            if (jsonStart < 0) return emptyList()
            val root = JSONObject(frame.substring(jsonStart))
            val metadata = root.optJSONArray("Metadata") ?: return emptyList()
            (0 until metadata.length()).mapNotNull { i ->
                val item = metadata.optJSONObject(i) ?: return@mapNotNull null
                if (item.optString("Type") != "WordBoundary") return@mapNotNull null
                val data = item.optJSONObject("Data") ?: return@mapNotNull null
                val offset = data.optLong("Offset", -1L)
                val duration = data.optLong("Duration", -1L)
                val word = data.optJSONObject("text")?.optString("Text").orEmpty()
                if (offset < 0 || duration < 0 || word.isEmpty()) return@mapNotNull null
                EdgeWordBoundary(offsetTicks = offset, durationTicks = duration, text = word)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Échec parsing word boundary : ${e.message}")
            emptyList()
        }
    }
}
