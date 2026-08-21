package com.inktone.domain.service

import kotlinx.coroutines.flow.StateFlow

/** Métadonnées du livre en cours de narration, affichées dans la notification média. */
data class PlaybackMetadata(
    val title: String? = null,
    val author: String? = null,
)

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

    /** Vrai quand l'audio est engagé (buffering ou lecture réelle). */
    val isPlaying: StateFlow<Boolean>

    /** Index de la phrase courante dans le chapitre. */
    val currentSentenceIndex: StateFlow<Int>

    /** Métadonnées (titre/auteur) du livre narré, pour la notification. */
    val metadata: StateFlow<PlaybackMetadata>

    /** Bascule lecture ↔ pause ; reprend depuis la phrase courante après un arrêt. */
    fun togglePlayPause()

    /** Recule (`delta < 0`) ou avance (`delta > 0`) d'une phrase. */
    fun skip(delta: Int)

    /** Arrête la lecture et libère la synthèse (reprise impossible sans [togglePlayPause]). */
    fun stop()
}
