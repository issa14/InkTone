package com.inktone.domain.service

import com.inktone.domain.model.ReadingMode

/**
 * Instantané des compteurs d'un [ReadingSessionTracker] à un instant donné.
 *
 * Un type nommé plutôt qu'un `Triple` : les trois grandeurs n'ont ni la même
 * unité ni le même sens, et les appelants les recopient dans un
 * `ReadingSession` où une inversion serait silencieuse.
 */
data class TrackerSnapshot(
    val visualMs: Long,
    val ttsMs: Long,
    val words: Int,
    val sentences: Int,
) {
    /** Durée totale de l'instantané — le seuil de persistance porte sur elle. */
    val totalMs: Long get() = visualMs + ttsMs
}

/**
 * Suivi temporel d'une session de lecture active (Lot Sessions).
 *
 * Accumule séparément le temps visuel et TTS sans jamais les
 * superposer : quand le TTS dicte le rythme, tout le temps écoulé
 * est imputé à [ttsDurationMs] ; le compteur visuel ne tourne que
 * lorsque l'utilisateur lit manuellement.
 *
 * Accumule également les mots et les phrases parcourus ([addProgress]),
 * alimentés par les deux chemins de lecture (narration et défilement
 * manuel), qui ne tournent jamais en même temps (K3). Ce compteur est ce
 * qui rend la vitesse de lecture calculable — sans lui, `wordsRead` reste
 * à zéro en base et tout KPI qui en dérive ment.
 *
 * Thread-safe uniquement si appelé depuis un seul thread
 * (le dispatcher principal du ViewModel). Toutes les méthodes sont
 * synchrones — le [clock] injectable permet les tests unitaires
 * sans `System.currentTimeMillis()`.
 *
 * @param publicationId identifiant de la publication lue
 * @param clock source de temps injectable (défaut System)
 */
class ReadingSessionTracker(
    val publicationId: String,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /** Temps visuel cumulé (lecture manuelle, TTS inactif). */
    var visualDurationMs: Long = 0
        private set

    /** Temps TTS cumulé (lecture audio). */
    var ttsDurationMs: Long = 0
        private set

    /** Mots parcourus depuis le dernier [reset] (tous chemins confondus). */
    var wordsRead: Int = 0
        private set

    /** Phrases parcourues depuis le dernier [reset]. */
    var sentencesRead: Int = 0
        private set

    /** Timestamp de création du tracker (posé une seule fois). */
    val startTimestamp: Long = clock()

    private var mode: ReadingMode = ReadingMode.VISUAL
    private var lastResumeMs: Long = startTimestamp
    private var _isPaused: Boolean = false

    /** Durée totale cumulée (visuel + TTS). */
    val totalMs: Long get() = visualDurationMs + ttsDurationMs

    /** Exposé pour que le timer puisse skipper un tracker au repos. */
    val isPaused: Boolean get() = _isPaused

    // ───── API publique ─────

    /** Reprend après une pause. No-op si déjà actif. */
    fun resume(mode: ReadingMode) {
        if (!_isPaused) return
        this.mode = mode
        lastResumeMs = clock()
        _isPaused = false
    }

    /** Flush les durées et marque comme pausé. Réservé à onCleared(). */
    fun pause() {
        if (_isPaused) return
        flush()
        _isPaused = true
    }

    /**
     * Bascule du mode courant vers [newMode] sans perdre de temps :
     * flush le mode actif, puis redémarre l'accumulation dans le
     * nouveau mode. L'appelant (playCurrentSentence / pausePlayback)
     * n'a pas besoin de gérer pause/resume — un seul appel suffit.
     */
    fun switchMode(newMode: ReadingMode) {
        flush()
        mode = newMode
        lastResumeMs = clock()
    }

    /**
     * Vrai si la durée totale dépasse le seuil minimal de 5 secondes.
     * Évite les micro-sessions parasites (ouverture/fermeture < 5s).
     */
    fun shouldPersist(): Boolean = totalMs >= 5_000L

    /**
     * Comptabilise [words] mots répartis sur [sentences] phrases réellement
     * parcourus. Appelée par le propriétaire courant du tracker — le Lecteur
     * pour le défilement manuel, le relais de narration ou le Lecteur pour les
     * phrases prononcées. Le tracker étant transmis et jamais partagé, il n'y a
     * jamais deux appelants concurrents.
     *
     * Les valeurs négatives sont ignorées : un recul de position ne retire
     * jamais des mots déjà lus.
     */
    fun addProgress(words: Int, sentences: Int = 1) {
        if (words <= 0) return
        wordsRead += words
        if (sentences > 0) sentencesRead += sentences
    }

    /**
     * Flush et retourne les compteurs cumulés SANS changer l'état
     * pause/reprise. Appelé par le timer (5 min) et ON_STOP pour
     * créer des fragments sans interrompre le tracking continu.
     */
    fun snapshot(): TrackerSnapshot {
        flush()
        return TrackerSnapshot(
            visualMs = visualDurationMs,
            ttsMs = ttsDurationMs,
            words = wordsRead,
            sentences = sentencesRead,
        )
    }

    /** Remet les accumulateurs à zéro après sauvegarde d'un fragment. */
    fun reset() {
        visualDurationMs = 0
        ttsDurationMs = 0
        wordsRead = 0
        sentencesRead = 0
    }

    // ───── Interne ─────

    private fun flush() {
        if (_isPaused) return
        val elapsed = clock() - lastResumeMs
        if (elapsed <= 0) return
        when (mode) {
            ReadingMode.VISUAL -> visualDurationMs += elapsed
            ReadingMode.AUDIO -> ttsDurationMs += elapsed
        }
        lastResumeMs = clock()
    }
}
