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
    fun buildSsml_applique_la_hauteur_relative() {
        // Avant l'audit UX (A16), la hauteur était figée à "+0Hz" quel que
        // soit le profil.
        assertTrue(EdgeTtsClient.buildSsml("x", "fr-FR-VivienneNeural", 1.0f).contains("pitch=\"+0%\""))
        assertTrue(EdgeTtsClient.buildSsml("x", "fr-FR-VivienneNeural", 1.0f, pitch = 1.2f).contains("pitch=\"+20%\""))
        assertTrue(EdgeTtsClient.buildSsml("x", "fr-FR-VivienneNeural", 1.0f, pitch = 0.8f).contains("pitch=\"-20%\""))
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
