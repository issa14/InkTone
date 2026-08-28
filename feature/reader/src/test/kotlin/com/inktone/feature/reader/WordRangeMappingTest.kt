package com.inktone.feature.reader

import com.inktone.domain.service.WordTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests JVM de [wordRangeAt] (Lot 16, Tâche 2.3) : la déduction du mot courant
 * depuis la position jouée est une fonction pure — bornes exactes, décalage de
 * début de phrase, silence inter-mots et phrase sans timestamps.
 */
class WordRangeMappingTest {

    private val timestamps = listOf(
        WordTimestamp(word = "Bonjour", startMs = 0, endMs = 200, charOffset = 0),
        WordTimestamp(word = "tout", startMs = 250, endMs = 450, charOffset = 8),
        WordTimestamp(word = "le", startMs = 500, endMs = 600, charOffset = 13),
    )

    @Test
    fun retourneLeMotCourant() {
        assertEquals(0 until 7, wordRangeAt(playedMs = 100, sentenceStartMs = 0, wordTimestamps = timestamps))
        assertEquals(8 until 12, wordRangeAt(playedMs = 300, sentenceStartMs = 0, wordTimestamps = timestamps))
        assertEquals(13 until 15, wordRangeAt(playedMs = 550, sentenceStartMs = 0, wordTimestamps = timestamps))
    }

    @Test
    fun respecteLesBornesExactes() {
        assertEquals(0 until 7, wordRangeAt(playedMs = 0, sentenceStartMs = 0, wordTimestamps = timestamps))
        assertNull(wordRangeAt(playedMs = 200, sentenceStartMs = 0, wordTimestamps = timestamps)) // fin exclue
        assertNull(wordRangeAt(playedMs = 249, sentenceStartMs = 0, wordTimestamps = timestamps)) // silence inter-mots
        assertEquals(8 until 12, wordRangeAt(playedMs = 250, sentenceStartMs = 0, wordTimestamps = timestamps)) // début inclus
    }

    @Test
    fun tientCompteDuDebutDePhrase() {
        // Phrase commençant à 1 000 ms de position jouée : ses mots sont
        // décalés d'autant, jamais relatifs au début du chapitre.
        assertEquals(0 until 7, wordRangeAt(playedMs = 1_050, sentenceStartMs = 1_000, wordTimestamps = timestamps))
        assertNull(wordRangeAt(playedMs = 999, sentenceStartMs = 1_000, wordTimestamps = timestamps))
    }

    @Test
    fun retourneNull_apresLeDernierMot() {
        assertNull(wordRangeAt(playedMs = 600, sentenceStartMs = 0, wordTimestamps = timestamps))
        assertNull(wordRangeAt(playedMs = 5_000, sentenceStartMs = 0, wordTimestamps = timestamps))
    }

    @Test
    fun retourneNull_sansTimestamps() {
        assertNull(wordRangeAt(playedMs = 100, sentenceStartMs = 0, wordTimestamps = emptyList()))
    }
}

/**
 * AUDIT_REACTIVITE_UX §5.3 — [msUntilNextWordBoundary] cadence le sondage
 * de [PlaybackOrchestrator] sur la durée réelle du mot plutôt qu'un
 * intervalle fixe : mêmes timestamps que [WordRangeMappingTest], mêmes
 * bornes exactes.
 */
class MsUntilNextWordBoundaryTest {

    private val timestamps = listOf(
        WordTimestamp(word = "Bonjour", startMs = 0, endMs = 200, charOffset = 0),
        WordTimestamp(word = "tout", startMs = 250, endMs = 450, charOffset = 8),
        WordTimestamp(word = "le", startMs = 500, endMs = 600, charOffset = 13),
    )

    @Test
    fun dansUnMot_attendSaFin() {
        assertEquals(100L, msUntilNextWordBoundary(playedMs = 100, sentenceStartMs = 0, wordTimestamps = timestamps))
        assertEquals(200L, msUntilNextWordBoundary(playedMs = 0, sentenceStartMs = 0, wordTimestamps = timestamps))
    }

    @Test
    fun dansUnSilenceInterMots_attendLeDebutDuProchainMot() {
        assertEquals(50L, msUntilNextWordBoundary(playedMs = 200, sentenceStartMs = 0, wordTimestamps = timestamps))
        assertEquals(1L, msUntilNextWordBoundary(playedMs = 249, sentenceStartMs = 0, wordTimestamps = timestamps))
    }

    @Test
    fun tientCompteDuDebutDePhrase() {
        assertEquals(100L, msUntilNextWordBoundary(playedMs = 1_100, sentenceStartMs = 1_000, wordTimestamps = timestamps))
    }

    @Test
    fun surLeDernierMot_attendSaFin() {
        assertEquals(50L, msUntilNextWordBoundary(playedMs = 550, sentenceStartMs = 0, wordTimestamps = timestamps))
    }

    @Test
    fun sansTimestamps_retourneZero() {
        assertEquals(0L, msUntilNextWordBoundary(playedMs = 100, sentenceStartMs = 0, wordTimestamps = emptyList()))
    }
}
