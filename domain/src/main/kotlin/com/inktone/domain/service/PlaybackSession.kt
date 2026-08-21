package com.inktone.domain.service

import kotlinx.coroutines.flow.StateFlow

/**
 * Métadonnées du livre en cours de narration, affichées dans la notification
 * média et dans le mini-lecteur (P2).
 *
 * [publicationId] est l'adresse de retour : elle permet au mini-lecteur de
 * ramener au Lecteur, sur le livre effectivement narré, depuis n'importe quel
 * écran. Nul tant qu'aucune narration n'a démarré.
 */
data class PlaybackMetadata(
    val publicationId: String? = null,
    val title: String? = null,
    val author: String? = null,
)

/** État de vie d'une session de lecture TTS (miroir domaine du `PlaybackStatus` interne). */
enum class PlaybackSessionState { IDLE, BUFFERING, PLAYING, PAUSED, ERROR }

/**
 * Façade de session de lecture TTS (P1, plan polissage Pareto).
 *
 * Sépare la notion de **session** (est-on en train de lire ? quelle phrase ?
 * quel livre ?) du contrat bas niveau [AudioPlayer] (qui ne connaît que des
 * segments PCM). Elle porte l'état de session et les commandes utilisateur
 * (lecture/pause, phrase ±, arrêt), consommées à la fois par la notification
 * média (`MediaSession`, écran verrouillé) et par l'écran Lecteur — une seule
 * source de vérité (K3, Blueprint §13.4), jamais deux chemins de contrôle.
 *
 * Contrat domaine, donc sans aucune dépendance Android : l'implémentation vit
 * dans la couche présentation (`feature/reader`) et est exposée via Hilt à
 * l'infrastructure (`infrastructure/media`) — sens des dépendances respecté.
 *
 * Sémantique actée : la pause de la notification est une **vraie pause**
 * (`pause()`/`resume()`, état `Paused`), distincte de l'arrêt (`stop()`,
 * état `Idle`) que l'écran Lecteur utilise pour « mettre en pause ».
 */
interface PlaybackSession {

    /** État de vie complet (distincte une pause réelle d'un arrêt, pour la notification). */
    val sessionState: StateFlow<PlaybackSessionState>

    /** Vrai quand l'audio est engagé (buffering ou lecture réelle). */
    val isPlaying: StateFlow<Boolean>

    /** Index de la phrase courante dans le chapitre. */
    val currentSentenceIndex: StateFlow<Int>

    /** Métadonnées (titre/auteur) du livre narré, pour la notification. */
    val metadata: StateFlow<PlaybackMetadata>

    /** Bascule lecture ↔ pause ; reprend depuis la phrase courante après un arrêt. */
    fun togglePlayPause()

    /**
     * Pause réelle, sans perdre la position ni vider la file (état `PAUSED`).
     *
     * Contrairement à [togglePlayPause], l'appel est **idempotent et
     * directionnel** : sans effet si la lecture n'est pas engagée. C'est ce
     * qu'exige une interruption externe (perte de focus audio, casque
     * débranché), où basculer aveuglément relancerait la narration au lieu de
     * l'interrompre.
     */
    fun pause()

    /** Reprend après [pause]. Sans effet si la session n'est pas en pause. */
    fun resume()

    /** Recule (`delta < 0`) ou avance (`delta > 0`) d'une phrase. */
    fun skip(delta: Int)

    /** Arrête la lecture et libère la synthèse (reprise impossible sans [togglePlayPause]). */
    fun stop()
}
