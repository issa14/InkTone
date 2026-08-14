package com.inktone.infrastructure.tts

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests JVM du protocole WebSocket Edge TTS (Tâche 2.4) via MockWebServer —
 * aucun réseau réel. Le parsing des word boundaries (`Path:audio.metadata`,
 * via `org.json`) n'est **pas** testé ici : `org.json` est une API Android
 * absente du classpath JVM ; ce chemin est couvert par le spike device
 * (Palier 1) et par `EdgeTtsEngine` en instrumenté (Palier 3).
 */
class EdgeTtsClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: EdgeTtsClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = EdgeTtsClient(OkHttpClient.Builder().build())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Serveur de test : capture les trames client et répond à la 2e trame. */
    private class CapturingServer(
        private val onBothFramesReceived: (WebSocket) -> Unit,
    ) : WebSocketListener() {
        val receivedText = mutableListOf<String>()

        override fun onMessage(webSocket: WebSocket, text: String) {
            receivedText.add(text)
            if (receivedText.size == 2) onBothFramesReceived(webSocket)
        }

        // Complète le handshake de fermeture côté serveur : sans ce echo,
        // MockWebServer reste en attente de fermeture et shutdown() lève
        // « Gave up waiting for queue to shut down ».
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }
    }

    private fun turnEndFrame(): String =
        "X-RequestId:x\r\nContent-Type:application/json; charset=utf-8\r\n" +
            "Path:turn.end\r\n\r\n{}"

    @Test
    fun synthese_collecte_les_chunks_binaires_et_complete_sur_turn_end() = runTest {
        val capture = CapturingServer { ws ->
            ws.send(ByteString.of(0x01.toByte(), 0x02.toByte(), 0x03.toByte()))
            ws.send(turnEndFrame())
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(capture))

        val result = client.synthesizeAt(
            server.url("/").toString(),
            "Bonjour",
            "fr-FR-VivienneNeural",
            1.0f,
        )

        assertEquals(3, result.mp3Bytes.size)
        assertTrue(result.wordBoundaries.isEmpty())
    }

    @Test
    fun envoie_la_config_puis_le_ssml_dans_l_ordre() = runTest {
        val capture = CapturingServer { ws -> ws.send(turnEndFrame()) }
        server.enqueue(MockResponse().withWebSocketUpgrade(capture))

        client.synthesizeAt(
            server.url("/").toString(),
            "Bonjour",
            "fr-FR-VivienneNeural",
            1.0f,
        )

        assertEquals(2, capture.receivedText.size)
        assertTrue(
            "première trame = speech.config",
            capture.receivedText[0].contains("Path:speech.config"),
        )
        assertTrue(
            "deuxième trame = ssml",
            capture.receivedText[1].contains("Path:ssml"),
        )
    }

    @Test
    fun erreur_permanente_ne_declenche_pas_de_retry() {
        // HTTP 403 (pas d'upgrade WebSocket) = erreur permanente → 1 seule
        // tentative, exception remontée, pas de retry (ADR-024 décision 5).
        server.enqueue(MockResponse().setResponseCode(403))

        var threw = false
        try {
            runTest {
                client.synthesizeAt(
                    server.url("/").toString(),
                    "Bonjour",
                    "fr-FR-VivienneNeural",
                    1.0f,
                )
            }
        } catch (e: Exception) {
            threw = true
        }

        assertTrue("une erreur doit être remontée", threw)
        assertEquals("aucun retry sur erreur permanente", 1, server.requestCount)
    }
}
