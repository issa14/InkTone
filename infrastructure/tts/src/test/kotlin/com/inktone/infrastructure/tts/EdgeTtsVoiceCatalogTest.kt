package com.inktone.infrastructure.tts

import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.availableVoicesFor
import com.inktone.domain.model.voiceLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les deux listes de voix Edge (catalogue du sélecteur dans le domaine,
 * validation dans le client) sont maintenues à la main : ce test empêche
 * qu'une voix proposée à l'utilisateur soit remplacée sans bruit par la voix
 * par défaut à la synthèse, ou qu'elle s'affiche sous son nom technique.
 */
class EdgeTtsVoiceCatalogTest {

    @Test
    fun le_selecteur_et_le_client_proposent_les_memes_voix() {
        assertEquals(availableVoicesFor(TtsEngineId.EDGE_TTS), EdgeTtsClient.VOICES)
    }

    @Test
    fun la_voix_par_defaut_fait_partie_des_voix() {
        assertTrue(EdgeTtsClient.DEFAULT_VOICE in EdgeTtsClient.VOICES)
    }

    @Test
    fun chaque_voix_a_un_libelle_lisible() {
        EdgeTtsClient.VOICES.forEach { voice ->
            assertNotEquals("libellé manquant pour $voice", voice, voiceLabel(voice))
        }
    }

    @Test
    fun les_voix_ajoutees_sont_presentes() {
        listOf("fr-FR-RemyMultilingualNeural", "fr-CH-ArianeNeural", "fr-BE-CharlineNeural")
            .forEach { assertTrue("$it absente", it in EdgeTtsClient.VOICES) }
    }
}
