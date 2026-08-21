package com.inktone.feature.player

import com.inktone.domain.service.PlaybackSessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Visibilité du mini-lecteur (P2), vérifiée en JVM : c'est la règle qui décide
 * si l'utilisateur a, ou non, un moyen d'arrêter la narration sans quitter
 * l'application.
 */
class MiniPlayerUiStateTest {

    private val narrating = MiniPlayerUiState(title = "Les Misérables", publicationId = "pub-1")

    @Test
    fun visible_pendant_la_lecture_la_synthese_et_la_pause() {
        listOf(
            PlaybackSessionState.PLAYING,
            PlaybackSessionState.BUFFERING,
            PlaybackSessionState.PAUSED,
        ).forEach { state ->
            assertTrue("$state doit afficher la barre", narrating.copy(sessionState = state).isVisible)
        }
    }

    @Test
    fun masque_quand_aucune_session_n_est_engagee() {
        listOf(PlaybackSessionState.IDLE, PlaybackSessionState.ERROR).forEach { state ->
            assertFalse("$state ne doit pas afficher la barre", narrating.copy(sessionState = state).isVisible)
        }
    }

    @Test
    fun masque_tant_qu_aucun_titre_n_est_connu() {
        // Une barre sans titre n'apprend rien et n'a nulle part où ramener.
        val sansTitre = narrating.copy(title = null, sessionState = PlaybackSessionState.PLAYING)
        assertFalse(sansTitre.isVisible)
    }
}
