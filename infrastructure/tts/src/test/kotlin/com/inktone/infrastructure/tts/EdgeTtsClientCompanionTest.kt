package com.inktone.infrastructure.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Tests purs des fonctions compagnon d'[EdgeTtsClient] (Tâche 2.4) :
 * algorithme `Sec-MS-GEC`, construction SSML, classification des erreurs
 * réseau. Déterministes, sans réseau.
 */
class EdgeTtsClientCompanionTest {

    @Test
    fun secMsGec_pour_ticks_connus_est_deterministe() {
        // SHA256("0" + TrustedClientToken) — valeur calculée indépendamment
        // (echo -n "06A5AA1D4EAFF4E9FB37E23D68491D6F4" | sha256sum).
        assertEquals(
            "7AB174E6E876C889B72314DC006A41035E841EA101B856325AD05528DCFE1A4A",
            EdgeTtsClient.secMsGecForTicks(0L),
        )
        // Déterministe : même ticks → même résultat, format 64 hex uppercase
        // (SHA256 = 32 octets = 64 caractères hex).
        val value = EdgeTtsClient.secMsGecForTicks(123_456_789L)
        assertEquals(value, EdgeTtsClient.secMsGecForTicks(123_456_789L))
        assertEquals(64, value.length)
        assertTrue(value.all { it in '0'..'9' || it in 'A'..'F' })
    }

    @Test
    fun buildSsml_echappe_le_texte_xml() {
        val ssml = EdgeTtsClient.buildSsml("a<b>&c'd\"e", "fr-FR-VivienneNeural", 1.0f)
        assertTrue(ssml.contains("a&lt;b&gt;&amp;c&apos;d&quot;e"))
        assertTrue(ssml.contains("fr-FR-VivienneNeural"))
    }

    @Test
    fun buildSsml_calcule_le_taux_de_vitesse() {
        assertTrue(EdgeTtsClient.buildSsml("x", "fr-FR-VivienneNeural", 1.0f).contains("+0%"))
        assertTrue(EdgeTtsClient.buildSsml("x", "fr-FR-VivienneNeural", 1.5f).contains("+50%"))
        assertTrue(EdgeTtsClient.buildSsml("x", "fr-FR-VivienneNeural", 0.75f).contains("-25%"))
    }

    @Test
    fun isNetworkError_classe_par_type_pas_par_message() {
        assertTrue(EdgeTtsClient.isNetworkError(UnknownHostException("dns")))
        assertTrue(EdgeTtsClient.isNetworkError(SocketTimeoutException("t")))
        assertTrue(EdgeTtsClient.isNetworkError(ConnectException("c")))
        assertTrue(EdgeTtsClient.isNetworkError(SocketException("s")))
        assertFalse(EdgeTtsClient.isNetworkError(IllegalStateException("Réseau indisponible")))
        assertFalse(EdgeTtsClient.isNetworkError(RuntimeException("boom")))
    }
}
