package com.inktone.infrastructure.media

import com.inktone.domain.service.PlaybackSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Garde-fous des règles d'interruption audio (P1-c). Ces règles décident
 * quand la voix se tait et — surtout — quand elle a le droit de repartir
 * seule : une reprise intempestive (dans une poche, après un débranchement de
 * casque) est le défaut le plus visible d'un lecteur audio, et le plus
 * silencieux à introduire lors d'une refonte ultérieure.
 */
class AudioInterruptionPolicyTest {

    private val policy = AudioInterruptionPolicy()

    @Test
    fun `appel entrant pendant la lecture met en pause puis reprend au retour du focus`() {
        assertEquals(
            InterruptionAction.PAUSE,
            policy.onInterruption(AudioInterruption.FOCUS_LOST_TRANSIENT, PlaybackSessionState.PLAYING),
        )
        assertEquals(
            InterruptionAction.RESUME,
            policy.onInterruption(AudioInterruption.FOCUS_GAINED, PlaybackSessionState.PAUSED),
        )
    }

    @Test
    fun `une perte transitoire avec atténuation autorisée met en pause, jamais en sourdine`() {
        // Contenu parlé : atténuer rend la voix inintelligible sans la faire
        // disparaître — le pire des deux mondes.
        assertEquals(
            InterruptionAction.PAUSE,
            policy.onInterruption(
                AudioInterruption.FOCUS_LOST_TRANSIENT_CAN_DUCK,
                PlaybackSessionState.PLAYING,
            ),
        )
    }

    @Test
    fun `une perte definitive met en pause sans jamais reprendre seule`() {
        assertEquals(
            InterruptionAction.PAUSE,
            policy.onInterruption(AudioInterruption.FOCUS_LOST, PlaybackSessionState.PLAYING),
        )
        assertFalse(policy.pausedByPolicy)
        assertEquals(
            InterruptionAction.NONE,
            policy.onInterruption(AudioInterruption.FOCUS_GAINED, PlaybackSessionState.PAUSED),
        )
    }

    @Test
    fun `un casque debranche met en pause et ne reprend pas au rebranchement`() {
        assertEquals(
            InterruptionAction.PAUSE,
            policy.onInterruption(AudioInterruption.BECAME_NOISY, PlaybackSessionState.PLAYING),
        )
        assertFalse(policy.pausedByPolicy)
        assertEquals(
            InterruptionAction.NONE,
            policy.onInterruption(AudioInterruption.FOCUS_GAINED, PlaybackSessionState.PAUSED),
        )
    }

    @Test
    fun `une interruption pendant la synthese differe la pause jusqu au premier son`() {
        // BUFFERING : la synthèse tourne, rien n'est encore audible — pauser
        // maintenant serait sans effet et l'audio démarrerait pendant l'appel.
        assertEquals(
            InterruptionAction.NONE,
            policy.onInterruption(AudioInterruption.FOCUS_LOST_TRANSIENT, PlaybackSessionState.BUFFERING),
        )
        assertEquals(
            InterruptionAction.NONE,
            policy.onSessionStateChanged(PlaybackSessionState.BUFFERING),
        )
        assertEquals(
            InterruptionAction.PAUSE,
            policy.onSessionStateChanged(PlaybackSessionState.PLAYING),
        )
    }

    @Test
    fun `une pause differee est abandonnee si la session s arrete entretemps`() {
        policy.onInterruption(AudioInterruption.FOCUS_LOST_TRANSIENT, PlaybackSessionState.BUFFERING)
        assertEquals(InterruptionAction.NONE, policy.onSessionStateChanged(PlaybackSessionState.IDLE))
        // Une lecture ultérieure, sans rapport, ne doit pas être pausée.
        assertEquals(InterruptionAction.NONE, policy.onSessionStateChanged(PlaybackSessionState.PLAYING))
        assertFalse(policy.pausedByPolicy)
    }

    @Test
    fun `une commande de l utilisateur desarme la reprise automatique`() {
        policy.onInterruption(AudioInterruption.FOCUS_LOST_TRANSIENT, PlaybackSessionState.PLAYING)
        policy.onUserCommand()
        assertEquals(
            InterruptionAction.NONE,
            policy.onInterruption(AudioInterruption.FOCUS_GAINED, PlaybackSessionState.PAUSED),
        )
    }

    @Test
    fun `une interruption hors lecture n arme aucune reprise`() {
        assertEquals(
            InterruptionAction.NONE,
            policy.onInterruption(AudioInterruption.FOCUS_LOST_TRANSIENT, PlaybackSessionState.IDLE),
        )
        assertFalse(policy.pausedByPolicy)
        assertEquals(
            InterruptionAction.NONE,
            policy.onInterruption(AudioInterruption.FOCUS_GAINED, PlaybackSessionState.IDLE),
        )
    }

    @Test
    fun `un focus refuse differe la pause sans armer de reprise`() {
        policy.onFocusDenied()
        assertFalse(policy.pausedByPolicy)
        assertEquals(InterruptionAction.PAUSE, policy.onSessionStateChanged(PlaybackSessionState.PLAYING))
        assertEquals(
            InterruptionAction.NONE,
            policy.onInterruption(AudioInterruption.FOCUS_GAINED, PlaybackSessionState.PAUSED),
        )
    }

    @Test
    fun `le retour du focus ne reprend pas si l utilisateur a deja relance la lecture`() {
        policy.onInterruption(AudioInterruption.FOCUS_LOST_TRANSIENT, PlaybackSessionState.PLAYING)
        // L'utilisateur relance depuis le Lecteur avant le retour du focus.
        assertEquals(
            InterruptionAction.NONE,
            policy.onInterruption(AudioInterruption.FOCUS_GAINED, PlaybackSessionState.PLAYING),
        )
        assertFalse(policy.pausedByPolicy)
    }
}
