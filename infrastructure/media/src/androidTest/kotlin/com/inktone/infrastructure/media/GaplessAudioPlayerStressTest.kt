package com.inktone.infrastructure.media

import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlayerState
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

/**
 * Test instrumenté de [GaplessAudioPlayer] (Tâche 2.1, ADR-025) — la
 * **porte d'entrée du lot 15** : aucun ordonnanceur tant que ce test ne passe
 * pas sur device.
 *
 * C'est ici que se prouve l'absence de SIGSEGV : les tests JVM du cœur
 * ([GaplessPlaybackCoreTest]) vérifient la discipline du verrou, mais seule
 * l'exécution sur un `AudioTrack` réel (thread natif + `release()` concurrents)
 * reproduit la course `stop pendant écriture` / `tap-stop répétés` qui
 * plantait l'`AudioSegmentPlayer` MODE_STATIC.
 *
 * NB : le « sans silence audible » entre deux segments ne peut pas s'asserter
 * mécaniquement ici (aucun flux de position — reporté au LOT 16). Ce test
 * prouve l'enchaînement sans erreur ni crash de 2+ segments ; la vérification
 * à l'oreille est la Tâche 2.2 (checklist device).
 */
@RunWith(AndroidJUnit4::class)
class GaplessAudioPlayerStressTest {

    private lateinit var player: GaplessAudioPlayer

    @Before
    fun setUp() {
        player = GaplessAudioPlayer()
    }

    @After
    fun tearDown() {
        player.release()
    }

    // ── Helpers ────────────────────────────────────────────

    /** Génère un segment PCM16 mono réel : sinusoïde à ~25 % du plein gain. */
    private fun segment(frequencyHz: Double, durationMs: Long, sampleRate: Int): AudioSegment {
        val frameCount = (sampleRate * durationMs / 1000L).toInt()
        val bytes = ByteArray(frameCount * 2)
        for (i in 0 until frameCount) {
            val sample = (sin(2 * PI * frequencyHz * i / sampleRate) * AMPLITUDE).toInt().toShort()
            bytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return AudioSegment(bytes, durationMs, wordTimestamps = emptyList(), sampleRate = sampleRate)
    }

    /** Attend que la file du lecteur soit vidée (segments remis à l'écriture). */
    private fun awaitPendingCountZero(timeoutMs: Long = 5_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (player.pendingCount == 0) return true
            Thread.sleep(10)
        }
        return player.pendingCount == 0
    }

    // ── Tests ──────────────────────────────────────────────

    @Test
    fun enchaineDeuxSegmentsSansErreur() {
        player.sampleRate = 22_050
        player.enqueue(segment(440.0, 150, 22_050))
        player.enqueue(segment(880.0, 150, 22_050))
        assertEquals(2, player.pendingCount)

        player.play()
        assertEquals(PlayerState.Playing, player.state.value)

        assertTrue("les deux segments doivent être consommés", awaitPendingCountZero())
        // Le lecteur ne s'arrête pas de lui-même en fin de file (l'ordonnanceur
        // décide de la fin de chapitre) : l'état reste Playing après écoulement.
        assertEquals(PlayerState.Playing, player.state.value)
    }

    @Test
    fun stopPendantEcriture_nePlantePas() {
        player.sampleRate = 22_050
        // Gros segment (2 s) : la boucle d'écriture est occupée sur plusieurs
        // chunks, ce qui maximise la probabilité que stop() saisisse le verrou
        // pendant une écriture (course use-after-free).
        val big = segment(440.0, 2_000, 22_050)
        repeat(50) {
            player.enqueue(big)
            player.play()
            Thread.sleep(2)
            player.stop()
            assertEquals(PlayerState.Stopped, player.state.value)
        }
    }

    @Test
    fun tapEtStopRepetes_nePlantePas() {
        player.sampleRate = 22_050
        // Enchaînement brutal play/stop : chaque play() recrée un track (à la
        // volée), chaque stop() le libère — la course release est exercée 100×.
        repeat(100) { i ->
            player.enqueue(segment(440.0, 80, 22_050))
            player.play()
            Thread.sleep(1)
            player.stop()
            assertEquals(PlayerState.Stopped, player.state.value)
        }
    }

    @Test
    fun pauseRepriseConserveLEtat() {
        player.sampleRate = 22_050
        player.enqueue(segment(440.0, 300, 22_050))
        player.play()
        assertEquals(PlayerState.Playing, player.state.value)

        player.pause()
        assertEquals(PlayerState.Paused, player.state.value)

        player.resume()
        assertEquals(PlayerState.Playing, player.state.value)
        assertTrue("le segment doit être consommé après reprise", awaitPendingCountZero())

        player.stop()
        assertEquals(PlayerState.Stopped, player.state.value)
    }

    @Test
    fun changementDeSampleRateAChaud_nePlantePas() {
        player.sampleRate = 22_050
        player.enqueue(segment(440.0, 200, 22_050))
        player.play()
        assertTrue(awaitPendingCountZero())

        // Changement à chaud : le track est recréé au nouveau sampleRate à la
        // prochaine écriture (ensureTrack re-valide le sampleRate par segment).
        player.sampleRate = 24_000
        player.enqueue(segment(880.0, 200, 24_000))
        assertTrue(awaitPendingCountZero())
        assertEquals(PlayerState.Playing, player.state.value)

        player.stop()
    }

    private companion object {
        const val AMPLITUDE = 8_192
    }
}
