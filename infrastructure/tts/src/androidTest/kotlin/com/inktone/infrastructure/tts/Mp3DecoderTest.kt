package com.inktone.infrastructure.tts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests instrumentés de [Mp3Decoder] (Tâche 2.4). Le décodage d'un MP3 réel
 * est déjà prouvé par le spike `EdgeTtsWebSocketSpikeTest` (Palier 1) et
 * sera couvert de bout en bout par `EdgeTtsEngine` (Palier 3) ; ici on
 * verrouille le comportement d'échec : un flux vide ou corrompu échoue
 * clairement, jamais un crash ni un silence.
 */
@RunWith(AndroidJUnit4::class)
class Mp3DecoderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val decoder = Mp3Decoder()

    @Test
    fun flux_vide_echoue_clairement() {
        try {
            kotlinx.coroutines.runBlocking {
                decoder.decode(byteArrayOf(), context.cacheDir)
            }
            fail("Un flux MP3 vide doit lever une exception, pas produire du silence")
        } catch (e: Exception) {
            // échec clair attendu (check(isNotEmpty) ou erreur de décodage)
        }
    }

    @Test
    fun flux_corrompu_echoue_clairement_sans_crash() {
        try {
            kotlinx.coroutines.runBlocking {
                decoder.decode(byteArrayOf(0x00, 0x01, 0x02, 0x03), context.cacheDir)
            }
            fail("Un flux MP3 corrompu doit lever une exception")
        } catch (e: Exception) {
            assertTrue(e is Exception) // jamais un crash natif, toujours une exception
        }
    }
}
