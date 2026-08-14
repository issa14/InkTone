package com.inktone.infrastructure.tts

import com.inktone.domain.service.AppliedText
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test JVM pur du mapping des frontières de mot Edge vers `WordTimestamp`
 * (Tâche 3.4) — la fonction `mapEdgeWordBoundaries` est déterministe et sans
 * Android, donc testable sans device.
 */
class EdgeTtsEngineMappingTest {

    @Test
    fun mappe_les_frontieres_en_wordTimestamps_sur_texte_original() {
        val applied = AppliedText.identity("Bonjour, ceci est une phrase.")
        val boundaries = listOf(
            EdgeWordBoundary(offsetTicks = 500_000L, durationTicks = 6_000_000L, text = "Bonjour"),
            EdgeWordBoundary(offsetTicks = 9_375_000L, durationTicks = 2_750_000L, text = "ceci"),
            EdgeWordBoundary(offsetTicks = 12_125_000L, durationTicks = 250_000L, text = "est"),
        )

        val result = mapEdgeWordBoundaries(boundaries, applied)

        assertEquals(3, result.size)
        // ticks 100 ns → ms : /10_000
        assertEquals("Bonjour", result[0].word)
        assertEquals(0, result[0].charOffset)
        assertEquals(50L, result[0].startMs)
        assertEquals(650L, result[0].endMs)
        assertEquals("ceci", result[1].word)
        assertEquals(9, result[1].charOffset)
        assertEquals("est", result[2].word)
        assertEquals(14, result[2].charOffset)
    }

    @Test
    fun mot_introuvable_est_ignore_sans_casser_la_suite() {
        val applied = AppliedText.identity("Un deux trois.")
        val boundaries = listOf(
            EdgeWordBoundary(offsetTicks = 100_000L, durationTicks = 500_000L, text = "inconnu"),
            EdgeWordBoundary(offsetTicks = 700_000L, durationTicks = 500_000L, text = "deux"),
        )

        val result = mapEdgeWordBoundaries(boundaries, applied)

        // « inconnu » introuvable → ignoré ; « deux » trouvé normalement.
        assertEquals(1, result.size)
        assertEquals("deux", result[0].word)
        assertEquals(3, result[0].charOffset)
    }
}
