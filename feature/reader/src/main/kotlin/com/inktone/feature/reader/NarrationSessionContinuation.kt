package com.inktone.feature.reader

import com.inktone.domain.model.ReadingMode
import com.inktone.domain.model.ReadingSession
import com.inktone.domain.repository.ReadingSessionRepository
import com.inktone.domain.service.PlaybackSession
import com.inktone.domain.service.PlaybackSessionState
import com.inktone.domain.service.ReadingSessionTracker
import com.inktone.domain.service.TrackerSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Relais du suivi statistique quand l'écran Lecteur disparaît mais que la
 * narration continue (P2-b).
 *
 * ## Le problème qu'il résout
 *
 * [ReadingSessionTracker] appartenait au `ReaderViewModel` seul. Depuis P1-d,
 * quitter le Lecteur laisse la voix continuer — mais le tracker mourait avec
 * l'écran, si bien qu'une heure d'écoute en fermant le Lecteur ne comptait pour
 * rien dans les statistiques. C'était l'écart 2 déclaré à l'issue de P1.
 *
 * ## Pourquoi un relais plutôt qu'un second propriétaire
 *
 * Une session de lecture (`ReadingSession`) ne peut avoir qu'un seul écrivain à
 * la fois, sans quoi les fragments se chevauchent et le temps est compté deux
 * fois. Le tracker n'est donc jamais *partagé* : il est **transmis**. L'écran le
 * cède en mourant ([continueTracking]), un écran rouvert le reprend
 * ([takeOver]), et entre les deux ce relais en est l'unique propriétaire.
 *
 * Le tracker étant un objet de domaine pur (compteurs + identifiant de
 * publication, aucune référence Android), le transmettre ne retient jamais le
 * ViewModel en mémoire.
 *
 * Le mode est forcé à [ReadingMode.AUDIO] : par construction, ce relais ne vit
 * que pendant une narration sans écran — il n'y a pas de lecture visuelle à
 * comptabiliser quand il n'y a plus rien à regarder.
 */
@Singleton
class NarrationSessionContinuation @Inject constructor(
    private val playbackSession: PlaybackSession,
    private val readingSessionRepository: ReadingSessionRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var tracker: ReadingSessionTracker? = null
    private var lastFragmentSavedMs: Long = 0
    private var job: Job? = null

    /**
     * Prend la main sur [tracker] : accumule le temps d'écoute en mode AUDIO,
     * pose un fragment toutes les [CHECKPOINT_INTERVAL_MS], et sauvegarde une
     * dernière fois quand la narration s'arrête pour de bon.
     *
     * @param lastFragmentSavedMs borne basse du prochain fragment, reprise du
     *   Lecteur : sans elle, le premier fragment de ce relais recouvrirait le
     *   temps déjà enregistré par l'écran (fragments non chevauchants).
     */
    fun continueTracking(tracker: ReadingSessionTracker, lastFragmentSavedMs: Long) {
        job?.cancel()
        this.tracker = tracker
        this.lastFragmentSavedMs = lastFragmentSavedMs
        tracker.resume(ReadingMode.AUDIO)
        tracker.switchMode(ReadingMode.AUDIO)

        job = scope.launch {
            launch { checkpointLoop() }
            // Chantier statistiques V1 — pendant ce relais, c'est ici que les
            // mots prononcés sont comptés : le collecteur du ReaderViewModel a
            // disparu avec son écran, et le tracker n'a qu'un propriétaire.
            launch {
                playbackSession.narratedSentenceWords.collect { words ->
                    this@NarrationSessionContinuation.tracker?.addProgress(words)
                }
            }
            // Attend la fin RÉELLE de la narration. `PAUSED` n'en est pas une :
            // l'utilisateur peut reprendre depuis la notification, et le temps
            // en pause n'est de toute façon pas compté (le tracker est pausé).
            playbackSession.sessionState.collect { state ->
                when (state) {
                    PlaybackSessionState.PAUSED -> this@NarrationSessionContinuation.tracker?.pause()
                    PlaybackSessionState.PLAYING, PlaybackSessionState.BUFFERING ->
                        this@NarrationSessionContinuation.tracker?.resume(ReadingMode.AUDIO)
                    PlaybackSessionState.IDLE, PlaybackSessionState.ERROR -> {
                        finish()
                        return@collect
                    }
                }
            }
        }
    }

    /**
     * Rend le tracker à un Lecteur qui rouvre le même livre, pour qu'il reprenne
     * le comptage là où il en est plutôt que d'ouvrir une seconde session
     * concurrente. `null` si aucun suivi n'est en cours, ou s'il porte sur une
     * autre publication (l'utilisateur a changé de livre : sa session d'écoute
     * précédente ne le concerne plus).
     *
     * @return le tracker et la borne du dernier fragment sauvegardé.
     */
    fun takeOver(publicationId: String): Handover? {
        val current = tracker ?: return null
        if (current.publicationId != publicationId) return null
        job?.cancel()
        job = null
        tracker = null
        return Handover(current, lastFragmentSavedMs)
    }

    /** Tracker rendu à un Lecteur, avec la borne de son dernier fragment. */
    data class Handover(val tracker: ReadingSessionTracker, val lastFragmentSavedMs: Long)

    /** Vrai quand ce relais suit une narration — exposé pour les tests. */
    fun isTracking(): Boolean = tracker != null

    private suspend fun checkpointLoop() {
        while (true) {
            delay(CHECKPOINT_INTERVAL_MS)
            val current = tracker ?: return
            if (current.isPaused) continue
            val snapshot = current.snapshot()
            if (snapshot.totalMs < MIN_FRAGMENT_MS) continue
            persistFragment(current.publicationId, snapshot)
            current.reset()
        }
    }

    /** Dernière sauvegarde puis abandon du tracker : la session est close. */
    private fun finish() {
        val current = tracker ?: return
        val snapshot = current.snapshot()
        if (snapshot.totalMs >= MIN_FRAGMENT_MS) {
            persistFragment(current.publicationId, snapshot)
            current.reset()
        }
        tracker = null
        job?.cancel()
        job = null
    }

    private fun persistFragment(publicationId: String, snapshot: TrackerSnapshot) {
        val fragmentStart = lastFragmentSavedMs
        lastFragmentSavedMs = System.currentTimeMillis()
        scope.launch {
            readingSessionRepository.insert(
                ReadingSession(
                    id = UUID.randomUUID().toString(),
                    publicationId = publicationId,
                    startedAt = fragmentStart,
                    endedAt = lastFragmentSavedMs,
                    mode = if (snapshot.ttsMs >= snapshot.visualMs) ReadingMode.AUDIO
                    else ReadingMode.VISUAL,
                    sentencesRead = snapshot.sentences,
                    wordsRead = snapshot.words,
                    visualDurationMs = snapshot.visualMs,
                    ttsDurationMs = snapshot.ttsMs,
                ),
            )
        }
    }

    private companion object {
        /** Même cadence que le Lecteur : un fragment toutes les 5 minutes. */
        const val CHECKPOINT_INTERVAL_MS = 5 * 60 * 1000L

        /** Même seuil que le Lecteur : pas de micro-fragment parasite. */
        const val MIN_FRAGMENT_MS = 5_000L
    }
}
