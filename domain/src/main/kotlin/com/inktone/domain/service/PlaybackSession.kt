package com.inktone.domain.service

import com.inktone.domain.model.SleepTimerState
import kotlinx.coroutines.flow.SharedFlow
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
    /**
     * Couverture du livre narré, pour la notification et l'écran verrouillé.
     * Chaîne d'URI et non bitmap : le domaine ne connaît pas `Bitmap`, et
     * charger l'image est le métier de la couche qui l'affiche.
     */
    val coverUri: String? = null,
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

    /**
     * Nombre de mots de chaque phrase **entièrement prononcée**, émis une
     * phrase à la fois (Chantier statistiques V1).
     *
     * Porté par la session et non par l'écran Lecteur, pour la même raison que
     * [sleepTimer] : écouter écran éteint est le cas nominal, et un comptage
     * qui mourrait avec l'écran ne créditerait jamais les longues écoutes.
     *
     * Une phrase entamée puis interrompue n'est pas émise — seul ce qui a été
     * réellement entendu compte.
     */
    val narratedSentenceWords: SharedFlow<Int>

    /** Métadonnées (titre/auteur) du livre narré, pour la notification. */
    val metadata: StateFlow<PlaybackMetadata>

    /** Bascule lecture ↔ pause ; reprend depuis la phrase courante après un arrêt. */
    fun togglePlayPause()

    /**
     * Démarre la narration d'une publication depuis sa position de reprise
     * enregistrée (K3), **sans écran Lecteur**.
     *
     * [togglePlayPause] ne sait relancer qu'une session déjà constituée
     * (phrases, voix, chapitre) : depuis la Bibliothèque, aucune ne l'est
     * encore. Cette fonction est le démarrage à froid — elle résout la
     * publication, sa position de reprise, sa voix et son programme de
     * chapitres, puis lance la lecture. C'est le même chemin de position que
     * le Lecteur (`ReadingState.locator`), jamais un départ implicite à zéro.
     *
     * Sans effet si la publication est introuvable, illisible, ou d'un format
     * sans narration (PDF, décision actée 16 du Lot 12). Idempotente vis-à-vis
     * du livre déjà narré : la relancer redémarre la narration de sa position
     * de reprise, ce que l'appelant doit éviter en consultant [metadata] et
     * [sessionState] (voir `LibraryViewModel.toggleResumeNarration`).
     */
    fun startNarration(publicationId: String)

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

    /**
     * Minuteur de sommeil en cours, `null` si aucun (P2-b).
     *
     * Porté par la session et non par l'écran Lecteur : s'endormir en écoutant
     * est précisément le cas où l'écran est éteint et l'écran de lecture
     * détruit. Un minuteur qui mourrait avec lui laisserait la narration
     * tourner toute la nuit.
     */
    val sleepTimer: StateFlow<SleepTimerState?>

    /**
     * Arme le minuteur pour [minutes], ou l'annule si `null`. Un seul minuteur
     * actif à la fois : réarmer remplace, jamais deux qui coexistent.
     */
    fun setSleepTimer(minutes: Int?)
}
