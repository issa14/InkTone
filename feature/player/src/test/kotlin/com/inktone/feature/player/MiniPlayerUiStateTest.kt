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

    // ───── Doublon avec la carte « Reprendre la lecture » ─────

    @Test
    fun masque_quand_la_carte_de_reprise_porte_deja_ce_livre() {
        // Depuis que la carte pilote la narration sans ouvrir le Lecteur,
        // lancer la lecture depuis la Bibliothèque y faisait apparaître un
        // SECOND lecture/pause pour le même livre.
        val doublon = narrating.copy(
            sessionState = PlaybackSessionState.PLAYING,
            isRedundantWithResumeCard = true,
        )
        assertFalse(doublon.isVisible)
    }

    @Test
    fun reste_affichee_quand_la_carte_porte_un_AUTRE_livre() {
        // Cas réel : on écoute A, on ouvre B pour le lire (`lastOpened` change,
        // la narration de A continue). La carte montre B — cette barre est
        // alors la SEULE prise sur A, et le seul chemin de retour vers lui.
        val autreLivre = narrating.copy(
            sessionState = PlaybackSessionState.PLAYING,
            isRedundantWithResumeCard = false,
        )
        assertTrue(autreLivre.isVisible)
    }

    @Test
    fun le_masquage_ne_vaut_que_pour_la_bibliotheque() {
        // `isRedundantWithResumeCard` n'est vrai que si l'appelant a signalé
        // que la carte est à l'écran : ailleurs (Récents, Statistiques,
        // Réglages), la barre reste le seul contrôle disponible.
        val ailleurs = narrating.copy(
            sessionState = PlaybackSessionState.PAUSED,
            isRedundantWithResumeCard = false,
        )
        assertTrue(ailleurs.isVisible)
    }
}
