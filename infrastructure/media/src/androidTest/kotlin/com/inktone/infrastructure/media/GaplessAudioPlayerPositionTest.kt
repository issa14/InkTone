package com.inktone.infrastructure.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.AudioSegment
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

/**
 * Test instrumenté du flux de position (Lot 16, Tâche 1.3) : la position
 * [GaplessAudioPlayer.playbackPosition] doit avancer de façon monotone,
 * se mettre à jour régulièrement (pas d'affame par le verrou d'écriture) et
 * atteindre ~la durée jouée, aux deux sample rates de production. C'est le
 * protocole du spike (`SPIKE_SURLIGNAGE_POSITION.md`) transformé en assertion
 * de non-régression, avec des bornes volontairement larges.
 */
@RunWith(AndroidJUnit4::class)
class GaplessAudioPlayerPositionTest {

    @Test
    fun positionAvanceDeFaconMonotone_etSansAffame() {
        verify(22_050)
        verify(24_000)
    }

    private fun verify(sampleRate: Int) {
        val player = GaplessAudioPlayer()
        player.sampleRate = sampleRate
        player.enqueue(sineSegment(sampleRate, 5_000))
        player.play()

        val startNanos = System.nanoTime()
        var previousPlayedMs = -1L
        var lastPlayedMs = -1L
        var monotonic = true
        var updates = 0

        while ((System.nanoTime() - startNanos) / 1_000_000L < 5_500L) {
            val position = player.playbackPosition.value
            if (position.valid && position.playedMs != previousPlayedMs) {
                if (previousPlayedMs >= 0 && position.playedMs < previousPlayedMs) {
                    monotonic = false
                }
                if (previousPlayedMs >= 0) updates++
                previousPlayedMs = position.playedMs
                lastPlayedMs = position.playedMs
            }
            Thread.sleep(20)
        }
        player.stop()
        player.release()

        assertTrue("sr=$sampleRate : la position doit être monotone", monotonic)
        assertTrue("sr=$sampleRate : trop peu de mises à jour ($updates) — position affamée", updates >= 5)
        assertTrue("sr=$sampleRate : position finale ${lastPlayedMs} ms, attendue ~5000 ms", lastPlayedMs in 3_500..6_000)
    }

    /** Sinus 440 Hz, PCM16 mono, [durationMs] millisecondes. */
    private fun sineSegment(sampleRate: Int, durationMs: Long): AudioSegment {
        val frames = (sampleRate * durationMs / 1_000L).toInt()
        val bytes = ByteArray(frames * 2)
        for (i in 0 until frames) {
            val sample = (sin(2 * PI * 440.0 * i / sampleRate) * AMPLITUDE).toInt().toShort()
            bytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return AudioSegment(bytes, durationMs, wordTimestamps = emptyList(), sampleRate = sampleRate)
    }

    private companion object {
        const val AMPLITUDE = 8_192
    }
}
