package com.inktone.feature.reader

import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.domain.model.ReadingMode
import com.inktone.domain.service.PlaybackMetadata
import com.inktone.domain.service.PlaybackSession
import com.inktone.domain.service.PlaybackSessionState
import com.inktone.domain.service.ReadingSessionTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fou de l'écart 2 de P1 : l'écoute qui continue après la fermeture du
 * Lecteur doit rester comptabilisée dans les statistiques.
 *
 * Le temps est piloté par une horloge injectée plutôt que par des `delay`
 * réels : ces cas portent sur ce qui est *imputé*, pas sur la cadence.
 */
class NarrationSessionContinuationTest {

    private class FakePlaybackSession : PlaybackSession {
        val state = MutableStateFlow(PlaybackSessionState.PLAYING)
        override val sessionState: StateFlow<PlaybackSessionState> get() = state
        override val isPlaying = MutableStateFlow(true)
        override val currentSentenceIndex = MutableStateFlow(0)
        override val metadata = MutableStateFlow(PlaybackMetadata())
        override fun togglePlayPause() = Unit
        override fun pause() = Unit
        override fun resume() = Unit
        override fun skip(delta: Int) = Unit
        override fun stop() = Unit
    }

    /** Horloge manuelle : le test décide du temps écoulé. */
    private class TestClock(var nowMs: Long = 1_000_000L) : () -> Long {
        override fun invoke(): Long = nowMs
    }

    @Test
    fun laFinDeLaNarrationEnregistreLeTempsDecouteEnAudio() = runBlocking {
        val session = FakePlaybackSession()
        val repository = FakeReadingSessionRepository()
        val continuation = NarrationSessionContinuation(session, repository)
        val clock = TestClock()
        val tracker = ReadingSessionTracker("pub1", clock)

        continuation.continueTracking(tracker, lastFragmentSavedMs = clock.nowMs)
        // 30 secondes d'écoute, écran détruit.
        clock.nowMs += 30_000L
        session.state.value = PlaybackSessionState.IDLE

        val sessions = withTimeout(5_000) {
            var found = repository.getAll()
            while (found.isEmpty()) {
                delay(10)
                found = repository.getAll()
            }
            found
        }

        assertEquals(1, sessions.size)
        val fragment = sessions.first()
        assertEquals("pub1", fragment.publicationId)
        assertEquals(ReadingMode.AUDIO, fragment.mode)
        assertEquals(30_000L, fragment.ttsDurationMs)
        assertEquals(
            "aucune seconde ne doit être imputée à la lecture visuelle : il n'y a plus d'écran",
            0L,
            fragment.visualDurationMs,
        )
    }

    @Test
    fun unLecteurRouvertReprendLeTrackerAuLieuDenOuvrirUnSecond() = runBlocking {
        val session = FakePlaybackSession()
        val continuation = NarrationSessionContinuation(session, FakeReadingSessionRepository())
        val clock = TestClock()
        val tracker = ReadingSessionTracker("pub1", clock)

        continuation.continueTracking(tracker, lastFragmentSavedMs = clock.nowMs)
        assertTrue(continuation.isTracking())

        val handover = continuation.takeOver("pub1")

        assertNotNull(handover)
        assertEquals(tracker, handover!!.tracker)
        assertTrue("le relais lâche la propriété du tracker", !continuation.isTracking())
    }

    @Test
    fun ouvrirUnAutreLivreNeRecupereRienDeLaSessionEnCours() = runBlocking {
        val session = FakePlaybackSession()
        val continuation = NarrationSessionContinuation(session, FakeReadingSessionRepository())
        val tracker = ReadingSessionTracker("pub1", TestClock())

        continuation.continueTracking(tracker, lastFragmentSavedMs = 0L)

        assertNull(
            "le tracker d'un autre livre ne doit jamais être réutilisé",
            continuation.takeOver("pub2"),
        )
        assertTrue("la narration en cours garde son suivi", continuation.isTracking())
    }

    @Test
    fun uneEcouteTropCourteNeCreePasDeFragmentParasite() = runBlocking {
        val session = FakePlaybackSession()
        val repository = FakeReadingSessionRepository()
        val continuation = NarrationSessionContinuation(session, repository)
        val clock = TestClock()
        val tracker = ReadingSessionTracker("pub1", clock)

        continuation.continueTracking(tracker, lastFragmentSavedMs = clock.nowMs)
        clock.nowMs += 2_000L // sous le seuil de 5 s
        session.state.value = PlaybackSessionState.IDLE

        // Laisse au collecteur le temps d'observer l'état terminal.
        withTimeout(5_000) {
            while (continuation.isTracking()) delay(10)
        }

        assertEquals(emptyList<Any>(), repository.getAll())
    }
}
