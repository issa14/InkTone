package com.inktone.infrastructure.media

import com.inktone.domain.service.PlaybackSessionState

/**
 * Interruption audio externe, telle que la voit la politique — indépendante
 * des constantes `AudioManager` (traduites par [AudioFocusController]).
 */
enum class AudioInterruption {
    /** Perte définitive du focus : une autre app prend la main durablement. */
    FOCUS_LOST,

    /** Perte transitoire : appel entrant, notification vocale, message audio. */
    FOCUS_LOST_TRANSIENT,

    /** Perte transitoire « avec atténuation autorisée » — voir la note ci-dessous. */
    FOCUS_LOST_TRANSIENT_CAN_DUCK,

    /** Retour du focus après une perte transitoire. */
    FOCUS_GAINED,

    /** Sortie audio devenue « bruyante » : casque ou Bluetooth débranché. */
    BECAME_NOISY,
}

/** Action à appliquer à la session de lecture. */
enum class InterruptionAction { NONE, PAUSE, RESUME }

/**
 * Politique d'interruption audio (P1-c, plan polissage Pareto) : décide
 * **quand** la narration se met en pause et **quand** elle reprend seule.
 *
 * Classe pure (aucun import Android) pour que ces règles — les plus faciles à
 * casser sans s'en apercevoir — soient couvertes par des tests JVM, sur le
 * modèle du couple `GaplessPlaybackCore` / `GaplessAudioPlayer` : la politique
 * ici, l'I/O `AudioManager` dans [AudioFocusController].
 *
 * Trois règles, toutes issues du fait que le contenu est de la **parole** :
 *
 * 1. **Jamais d'atténuation (« ducking »).** Baisser le volume d'une voix
 *    synthétique ne la rend pas discrète, elle la rend inintelligible — et
 *    l'auditeur perd le fil du texte. `CAN_DUCK` est donc traité exactement
 *    comme une perte transitoire : on pause. C'est aussi la recommandation
 *    Android pour les contenus parlés (`setWillPauseWhenDucked(true)`).
 * 2. **Reprise automatique seulement après une perte transitoire.** Sur une
 *    perte définitive, l'utilisateur a délibérément lancé autre chose :
 *    reprendre par-dessus serait une agression.
 * 3. **Jamais de reprise après un débranchement de casque.** Le casque
 *    rebranché ne signifie pas « reprends la lecture à voix haute » — c'est
 *    précisément le scénario où la narration repartirait dans le haut-parleur
 *    d'une poche.
 *
 * Le drapeau [pausedByPolicy] n'est armé que dans le cas 2. Toute commande
 * explicite de l'utilisateur ([onUserCommand]) le désarme : à partir de là,
 * l'état de lecture lui appartient, la politique ne le reprendra pas.
 *
 * Cas particulier `BUFFERING` : à la perte du focus, la synthèse peut être
 * lancée sans qu'aucun son ne soit encore audible. Une pause serait sans
 * effet (rien à suspendre) et l'audio démarrerait *pendant* l'appel entrant.
 * La pause est donc **différée** et appliquée à la première transition vers
 * `PLAYING` ([onSessionStateChanged]).
 */
class AudioInterruptionPolicy {

    /** Vrai si c'est la politique qui a suspendu la lecture, et qu'elle peut la reprendre. */
    var pausedByPolicy: Boolean = false
        private set

    /** Vrai si une pause a été décidée alors que rien n'était encore audible. */
    var pauseDeferred: Boolean = false
        private set

    /** Décide de l'action à appliquer face à [interruption], vu l'état [state]. */
    fun onInterruption(
        interruption: AudioInterruption,
        state: PlaybackSessionState,
    ): InterruptionAction = when (interruption) {
        AudioInterruption.FOCUS_GAINED -> {
            if (pausedByPolicy && state == PlaybackSessionState.PAUSED) {
                pausedByPolicy = false
                pauseDeferred = false
                InterruptionAction.RESUME
            } else {
                // Focus regagné alors que nous n'avions rien suspendu (ou que
                // l'utilisateur a repris/arrêté entretemps) : ne rien forcer.
                pausedByPolicy = false
                InterruptionAction.NONE
            }
        }

        AudioInterruption.FOCUS_LOST_TRANSIENT,
        AudioInterruption.FOCUS_LOST_TRANSIENT_CAN_DUCK,
        -> pauseFor(state, resumable = true)

        AudioInterruption.FOCUS_LOST -> pauseFor(state, resumable = false)

        AudioInterruption.BECAME_NOISY -> pauseFor(state, resumable = false)
    }

    /**
     * À appeler à chaque changement d'état de session : applique une pause
     * différée quand la lecture devient réellement audible, et abandonne le
     * différé si la session s'est arrêtée entretemps.
     */
    fun onSessionStateChanged(state: PlaybackSessionState): InterruptionAction {
        if (!pauseDeferred) return InterruptionAction.NONE
        return when (state) {
            PlaybackSessionState.PLAYING -> {
                pauseDeferred = false
                InterruptionAction.PAUSE
            }
            PlaybackSessionState.IDLE, PlaybackSessionState.ERROR -> {
                // Plus rien à suspendre : le différé n'a plus d'objet, et le
                // conserver ferait pauser une lecture future sans rapport.
                pauseDeferred = false
                pausedByPolicy = false
                InterruptionAction.NONE
            }
            PlaybackSessionState.BUFFERING, PlaybackSessionState.PAUSED -> InterruptionAction.NONE
        }
    }

    /**
     * Focus refusé par le système (un appel est déjà en cours, par exemple).
     *
     * Rien n'est encore engagé à cet instant — le service démarre en même
     * temps que la narration — donc la pause est **différée** et s'appliquera
     * dès que la lecture deviendra audible. Aucune reprise automatique : le
     * focus n'ayant jamais été obtenu, aucun `AUDIOFOCUS_GAIN` ne viendra.
     */
    fun onFocusDenied() {
        pausedByPolicy = false
        pauseDeferred = true
    }

    /**
     * À appeler dès que l'utilisateur commande lui-même la lecture (bouton du
     * Lecteur, action de la notification, écran verrouillé) : la politique
     * renonce à toute reprise automatique.
     */
    fun onUserCommand() {
        pausedByPolicy = false
        pauseDeferred = false
    }

    private fun pauseFor(state: PlaybackSessionState, resumable: Boolean): InterruptionAction =
        when (state) {
            PlaybackSessionState.PLAYING -> {
                pausedByPolicy = resumable
                pauseDeferred = false
                InterruptionAction.PAUSE
            }
            PlaybackSessionState.BUFFERING -> {
                pausedByPolicy = resumable
                pauseDeferred = true
                InterruptionAction.NONE
            }
            PlaybackSessionState.PAUSED,
            PlaybackSessionState.IDLE,
            PlaybackSessionState.ERROR,
            -> {
                // Rien n'est engagé : ne pas armer la reprise, sinon un retour
                // de focus relancerait une narration que personne n'a demandée.
                pausedByPolicy = false
                pauseDeferred = false
                InterruptionAction.NONE
            }
        }
}
