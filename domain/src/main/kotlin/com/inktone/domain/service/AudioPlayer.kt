package com.inktone.domain.service

import kotlinx.coroutines.flow.StateFlow

/**
 * Contrat de lecture audio gapless (Lot 15, ADR-025). Seule abstraction que
 * la couche présentation/application consomme pour émettre du PCM : le lecteur
 * réel ([AudioTrack] en `MODE_STREAM`) vit dans `infrastructure/media`, et ce
 * contrat vit en `domain` pour respecter le sens des dépendances
 * (`Presentation → Application → Domain ← Data ← Infrastructure`).
 *
 * Le lecteur est un consommateur passif de segments : [enqueue] ajoute un
 * segment PCM16 dans une file non-bloquante, [play] démarre l'écoulement de la
 * file. L'ordonnanceur (couche présentation) est le producteur qui décide de
 * l'ordre des segments et des silences ponctués — le lecteur n'a aucune
 * notion de phrase, de chapitre ou de surlignage.
 *
 * **Aucun flux de position** dans ce contrat : la synchronisation du
 * surlignage par position réelle est reportée au LOT 16 (spike d'abord). Tant
 * que ce flux n'existe pas, rien ne consomme la position `AudioTrack`.
 *
 * Garanties de contrat pour toute implémentation :
 * - **Thread-safe** : les méthodes peuvent être appelées depuis n'importe
 *   quel thread (coroutines, callbacks natifs AudioTrack). Les transitions
 *   d'état et la libération des ressources natives sont sérialisées par
 *   l'implémentation (verrou + flag d'arrêt) — jamais de double `release()`.
 * - **[enqueue] non-bloquant** : retourne immédiatement, jamais de synthèse,
 *   jamais d'I/O longue. La production (synthèse TTS) reste du ressort de
 *   l'ordonnanceur.
 * - **PCM16 signé little-endian** : [AudioSegment.audioData] est écrit tel
 *   quel. Aucune conversion Float→Short, aucun gain (écart acté vis-à-vis du
 *   legacy qui compensait le volume faible de Piper).
 * - **[release] terminal** : après appel, aucune autre méthode ne doit être
 *   invoquée ; les ressources natives sont libérées exactement une fois.
 */
interface AudioPlayer {

    /**
     * Met en file un segment PCM16 à jouer. Retour immédiat. L'ordre d'enqueue
     * est l'ordre de lecture (FIFO). Ne démarre rien : [play] est requis pour
     * lancer (ou reprendre) l'écoulement de la file.
     */
    fun enqueue(segment: AudioSegment)

    /** Démarre (ou reprend) la lecture de la file. Idempotent si déjà en lecture. */
    fun play()

    /** Suspend la lecture sans vider la file ni perdre la position du segment courant. */
    fun pause()

    /** Reprend la lecture là où [pause] l'avait laissée. */
    fun resume()

    /** Arrête la lecture et vide la file des segments en attente. */
    fun stop()

    /**
     * Libère définitivement les ressources natives sous-jacentes. Terminable :
     * plus aucun appel après celui-ci. Sûr même si déjà à l'arrêt.
     */
    fun release()

    /**
     * Volume linéaire de lecture, dans `[0.0, 1.0]`. Appliqué à la source
     * audio sous-jacente, sans toucher au gain du segment lui-même.
     */
    fun setVolume(volume: Float)

    /**
     * Fréquence d'échantillonnage (Hz) utilisée pour configurer le lecteur.
     * Mutable : un changement de moteur (ex. Edge 24 kHz → Sherpa 22 050 Hz)
     * peut imposer une reconfiguration à chaud du lecteur.
     */
    var sampleRate: Int

    /** État courant du lecteur, émis en continu. */
    val state: StateFlow<PlayerState>

    /** Nombre de segments encore en attente dans la file (non écrits). */
    val pendingCount: Int
}

/**
 * États de vie d'un [AudioPlayer]. Transitions nominales :
 * `Idle → Playing → Paused → Playing → Stopped → Idle`.
 */
sealed interface PlayerState {
    /** Aucune lecture, file vide, ressources prêtes (ou jamais démarrées). */
    data object Idle : PlayerState

    /** Audio en cours d'écoulement. */
    data object Playing : PlayerState

    /** Lecture suspendue, position du segment courant conservée. */
    data object Paused : PlayerState

    /** Lecture arrêtée, file vidée par [AudioPlayer.stop]. */
    data object Stopped : PlayerState
}
