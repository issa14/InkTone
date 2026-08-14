package com.inktone.infrastructure.media

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlayerState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

/**
 * Harnais d'audition du lecteur isolé (Tâche 2.2) — **instrument de mesure,
 * pas un test d'assertion** : il fait entendre sur device la séquence de
 * phases décrite ci-dessous, pour remplir
 * `docs/device-verification/lot15-lecteur-seul.md`. Le verdict est un acte
 * d'Issa (écoute humaine), pas une assertion.
 *
 * Lancer uniquement ce test (sinon la suite de stress tourne en même temps) :
 * ```
 * ./gradlew :infrastructure:media:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.inktone.infrastructure.media.GaplessAudioPlayerAuditionTest
 * ```
 *
 * Séquence audible (chaque phase est aussi loggée dans logcat, tag
 * [GaplessAudition]) :
 * 1. **Sherpa 22 050 Hz** — 3 tons enchaînés (440/554/659 Hz) : le silence
 *    entre tons trahit un trou (gapless attendu).
 * 2. **Pause** (~1 s de silence), puis **reprise**.
 * 3. **Volume réduit** (~50 %) — un ton à 440 Hz plus faible.
 * 4. **Edge 24 kHz** — 3 tons enchaînés (523/659/784 Hz).
 * 5. **Stop** propre (silence final).
 */
@RunWith(AndroidJUnit4::class)
class GaplessAudioPlayerAuditionTest {

    private lateinit var player: GaplessAudioPlayer

    @Before
    fun setUp() {
        player = GaplessAudioPlayer()
    }

    @After
    fun tearDown() {
        player.release()
    }

    @Test
    fun auditionLecteurIsole() {
        // ── Phase 1 : Sherpa 22 050 Hz, 3 tons enchaînés ──────────────
        log("PHASE 1 — Sherpa 22 050 Hz : 3 tons enchaînés (gapless attendu)")
        player.sampleRate = 22_050
        player.enqueue(tone(440.0, 700, 22_050))
        player.enqueue(tone(554.0, 700, 22_050))
        player.enqueue(tone(659.0, 700, 22_050))
        player.play()
        Thread.sleep(3_500)

        // ── Phase 2 : pause puis reprise ──────────────────────────────
        log("PHASE 2 — pause (~1 s) puis reprise")
        player.pause()
        Thread.sleep(1_000)
        player.resume()
        Thread.sleep(1_500)

        // ── Phase 3 : volume réduit ───────────────────────────────────
        log("PHASE 3 — volume réduit (~50 %) : un ton plus faible")
        player.setVolume(0.5f)
        player.enqueue(tone(440.0, 700, 22_050))
        Thread.sleep(1_500)

        // ── Phase 4 : Edge 24 kHz, 3 tons enchaînés ───────────────────
        log("PHASE 4 — Edge 24 kHz : 3 tons enchaînés")
        player.stop()
        player.sampleRate = 24_000
        player.setVolume(1.0f)
        player.enqueue(tone(523.0, 700, 24_000))
        player.enqueue(tone(659.0, 700, 24_000))
        player.enqueue(tone(784.0, 700, 24_000))
        player.play()
        Thread.sleep(3_500)

        // ── Phase 5 : stop propre ─────────────────────────────────────
        log("PHASE 5 — stop propre (silence final)")
        player.stop()
        assertEquals(PlayerState.Stopped, player.state.value)
        log("AUDITION TERMINÉE")
    }

    /** Génère un ton sinusoïdal PCM16 mono à ~25 % du plein gain. */
    private fun tone(frequencyHz: Double, durationMs: Long, sampleRate: Int): AudioSegment {
        val frameCount = (sampleRate * durationMs / 1000L).toInt()
        val bytes = ByteArray(frameCount * 2)
        for (i in 0 until frameCount) {
            val sample = (sin(2 * PI * frequencyHz * i / sampleRate) * AMPLITUDE).toInt().toShort()
            bytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return AudioSegment(bytes, durationMs, wordTimestamps = emptyList(), sampleRate = sampleRate)
    }

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "GaplessAudition"
        const val AMPLITUDE = 8_192
    }
}
