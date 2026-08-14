package com.inktone.infrastructure.media

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests JVM de [estimatePlayedFrame] (Lot 16, Tâche 1.3) : le calcul pur de la
 * frame jouée à partir d'un échantillon `getTimestamp()` — extrait de la couche
 * I/O pour être testable sans Android.
 */
class PlaybackPositionEstimatorTest {

    @Test
    fun extrapoleLaFrameDepuisLeTimestamp() {
        // Frame 1000 présentée il y a 100 ms, à 24 000 Hz → +2400 frames.
        val played = estimatePlayedFrame(
            timestampFramePosition = 1_000,
            timestampNanoTime = 1_000_000_000L,
            nowNanoTime = 1_100_000_000L,
            sampleRate = 24_000,
        )
        assertEquals(3_400L, played)
    }

    @Test
    fun sansEcoulementLaFrameEstCelleDuTimestamp() {
        val played = estimatePlayedFrame(
            timestampFramePosition = 1_000,
            timestampNanoTime = 1_000_000_000L,
            nowNanoTime = 1_000_000_000L,
            sampleRate = 22_050,
        )
        assertEquals(1_000L, played)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sampleRateNulEstRefuse() {
        estimatePlayedFrame(0, 0, 0, 0)
    }
}
