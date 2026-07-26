package com.inktone.infrastructure.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Vérifie la logique de résolution des paramètres bruts d'`onRangeStart`
 * contre les deux sémantiques possibles (documentée par Android, et
 * celle observée empiriquement en Tâche 3.1.0 sur le device de test).
 * Ne nécessite pas de device — pure fonction sur des entiers.
 */
class ResolveWordBoundaryTest {

    private val textLength = 46 // "Bonjour, ceci est un test de synchronisation."

    @Test
    fun semantique_documentee_start_end_utilisee_quand_valide() {
        // start=0, end=7 sont un intervalle caractere valide : interpretation documentee retenue.
        val boundary = resolveWordBoundary(start = 0, end = 7, frame = 12345, textLength = textLength)
        assertEquals(WordBoundary(charStart = 0, charEnd = 7, audioFrame = 12345), boundary)
    }

    @Test
    fun semantique_empirique_end_frame_utilisee_quand_start_hors_bornes() {
        // Cas reel observe sur device : start est une position audio (hors bornes texte),
        // le vrai intervalle caractere est (end, frame).
        val boundary = resolveWordBoundary(start = 359, end = 0, frame = 7, textLength = textLength)
        assertEquals(WordBoundary(charStart = 0, charEnd = 7, audioFrame = 359), boundary)
    }

    @Test
    fun aucune_semantique_valide_retourne_null() {
        val boundary = resolveWordBoundary(start = 999, end = 999, frame = 999, textLength = textLength)
        assertNull(boundary)
    }

    @Test
    fun intervalle_vide_start_egal_end_est_valide() {
        val boundary = resolveWordBoundary(start = 5, end = 5, frame = 0, textLength = textLength)
        assertEquals(WordBoundary(charStart = 5, charEnd = 5, audioFrame = 0), boundary)
    }

    @Test
    fun toutes_les_sept_frontieres_du_spike_se_resolvent_correctement() {
        // Donnees brutes reelles capturees en Tache 3.1.0 (device V2206).
        val raw = listOf(
            Triple(359, 0, 7) to "Bonjour",
            Triple(17639, 9, 13) to "ceci",
            Triple(25800, 14, 17) to "est",
            Triple(29040, 18, 20) to "un",
            Triple(31560, 21, 25) to "test",
            Triple(39240, 26, 28) to "de",
            Triple(41880, 29, 44) to "synchronisation",
        )
        val sentence = "Bonjour, ceci est un test de synchronisation."

        raw.forEach { (triple, expectedWord) ->
            val (start, end, frame) = triple
            val boundary = resolveWordBoundary(start, end, frame, sentence.length)
            assertEquals(expectedWord, boundary?.let { sentence.substring(it.charStart, it.charEnd) })
        }
    }
}
