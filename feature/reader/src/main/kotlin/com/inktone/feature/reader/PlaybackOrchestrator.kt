package com.inktone.feature.reader

import com.inktone.domain.model.Chapter
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.cleanedAuthorsForDisplay
import com.inktone.domain.model.cleanedForDisplay
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.SleepTimerState
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioPlayer
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.ChapterParser
import com.inktone.domain.service.PlaybackMetadata
import com.inktone.domain.service.PlaybackSession
import com.inktone.domain.service.PlaybackSessionState
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationParser
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.service.TtsSegmentCache
import com.inktone.domain.service.WordTimestamp
import com.inktone.domain.service.sha256Hex
import com.inktone.domain.service.ttsCacheKey
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.repository.PronunciationRuleRepository
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.VoiceProfileRepository
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ordonnanceur de lecture gapless (Lot 15, ADR-025) — **borné**, sans preWarm
 * ni seuils d'erreur adaptatifs. Il ne connaît que les contrats domaine
 * [TtsEngine] et [AudioPlayer] — jamais `AudioTrack`, jamais un moteur concret.
 *
 * Depuis P1/P2 (plan polissage Pareto) il porte aussi la **session** :
 * métadonnées, programme de narration, auto-avance de chapitre et minuteur de
 * sommeil. Ces éléments vivaient dans le `ReaderViewModel` et mouraient donc
 * avec l'écran de lecture, alors qu'ils décrivent une écoute qui lui survit.
 * Le focus audio reste hors d'ici : c'est de l'I/O Android, porté par
 * `AudioFocusController` (infrastructure).
 *
 * Architecture producteur/consommateur :
 * - **Producteur** : synthétise les phrases dans l'ordre, avec un [Channel]
 *   borné à [LOOKAHEAD] (la synthèse de la phrase n+2 chevauche ainsi la
 *   lecture de la phrase n, sans courir plus loin que nécessaire). Un timeout
 *   de synthèse **unique** pour tous les moteurs ; en cas d'échec, un silence
 *   court est injecté et la lecture poursuit.
 * - **Consommateur** : enfile chaque [AudioSegment] + son silence ponctué dans
 *   le lecteur, appelle `play()` au premier, puis **pace** l'avancement de
 *   l'index de phrase à la vitesse de lecture (durées cumulées) — c'est ce qui
 *   déclenche le surlignage côté ViewModel, sans flux de position (LOT 16).
 *
 * [playGeneration] (AtomicLong) invalide les coroutines d'une génération
 * précédente : toute relance (ou arrêt, Tâche 3.2) fait sortir proprement le
 * producteur et le consommateur de l'ancienne génération — jamais deux
 * pipelines en concurrence.
 */
@Singleton
class PlaybackOrchestrator @Inject constructor(
    private val ttsEngine: TtsEngine,
    private val audioPlayer: AudioPlayer,
    private val updateReadingState: UpdateReadingStateUseCase,
    private val getReadingState: GetReadingStateUseCase,
    private val chapterParser: ChapterParser,
    // Démarrage à froid depuis la Bibliothèque ([startNarration]) : lancer une
    // narration sans écran Lecteur exige de résoudre soi-même la publication,
    // son spine, sa position de reprise et sa voix — le Lecteur ne peut plus
    // les apporter, puisqu'il n'est pas ouvert.
    private val publicationRepository: PublicationRepository,
    private val publicationParser: PublicationParser,
    private val preferencesRepository: PreferencesRepository,
    private val voiceProfileRepository: VoiceProfileRepository,
    private val ttsSegmentCache: TtsSegmentCache,
    private val pronunciationRuleRepository: PronunciationRuleRepository,
) : PlaybackSession {

    /**
     * Programme de narration (P2-b) : la suite ordonnée des chapitres du livre
     * narré, **par href** et non par contenu.
     *
     * C'est ce qui permet à l'auto-avance de chapitre de survivre à la
     * destruction de l'écran Lecteur : l'ordonnanceur n'a plus besoin qu'on lui
     * apporte les phrases du chapitre suivant, il les obtient lui-même de
     * [ChapterParser] (qui cache déjà le résultat). Auparavant, la chaîne
     * complète passait par le ViewModel — quitter le Lecteur arrêtait donc la
     * narration à la fin du chapitre en cours.
     *
     * On ne retient volontairement pas les phrases ici : un livre entier de
     * phrases en mémoire, pour un seul chapitre utile à la fois, dupliquerait
     * l'état déjà porté par l'écran et deviendrait périmé au moindre
     * rechargement.
     *
     * Le PDF passe par le même chemin depuis le 2026-08-26
     * ([PdfChapterParser]) : une page `page-N` se recharge comme un chapitre
     * EPUB, il n'y a donc rien de particulier à retenir ici pour ce format.
     */
    data class NarrationProgram(
        val publicationId: String,
        /** Hrefs des chapitres, **dans l'ordre du spine** : la position dans la liste EST l'index de chapitre. */
        val chapterHrefs: List<String>,
        /**
         * Un chapitre sans phrase interrompt-il la narration, ou se saute-t-il ?
         *
         * Faux pour l'EPUB : un chapitre vide y signale un échec de parsing,
         * et sauter par-dessus ferait perdre du texte à l'auditeur sans
         * qu'il le sache. Vrai pour le PDF, où une page sans texte est une
         * planche ou une illustration — parfaitement normale, et sans rien
         * à narrer (ADR-017 volet 2).
         */
        val skipEmptyChapters: Boolean = false,
    )

    /** État de lecture exposé à la couche présentation. */
    sealed interface PlaybackStatus {
        data object Idle : PlaybackStatus
        data object Buffering : PlaybackStatus
        data object Playing : PlaybackStatus
        data object Paused : PlaybackStatus
        data class Error(val message: String) : PlaybackStatus
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<PlaybackStatus>(PlaybackStatus.Idle)
    val state: StateFlow<PlaybackStatus> = _state.asStateFlow()

    /** Index de la phrase en cours de lecture (surlignage, progression). */
    private val _currentSentenceIndex = MutableStateFlow(0)
    override val currentSentenceIndex: StateFlow<Int> = _currentSentenceIndex.asStateFlow()

    /**
     * Index du chapitre en cours de narration (P2-b).
     *
     * Émis par l'ordonnanceur, **suivi** par l'écran Lecteur — et non l'inverse.
     * C'est le renversement qu'impose une session qui survit à l'écran : quand
     * l'auto-avance change de chapitre alors que le Lecteur est détruit, ce flux
     * porte la vérité, et l'écran s'y recale s'il revient.
     */
    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    /**
     * Mots des phrases **effectivement prononcées jusqu'au bout**, émis une
     * phrase à la fois (Chantier statistiques V1).
     *
     * Émis ici et non depuis le Lecteur parce que l'ordonnanceur est le seul à
     * survivre à la destruction de l'écran : une heure d'écoute écran éteint
     * doit compter comme le reste. Le propriétaire courant du tracker de
     * session — `ReaderViewModel` ou `NarrationSessionContinuation`, jamais les
     * deux — collecte ce flux et le reporte au tracker.
     *
     * Une phrase n'est comptée qu'après avoir été jouée entièrement : une
     * narration coupée en plein milieu ne crédite pas la phrase en cours.
     */
    private val _narratedSentenceWords = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    override val narratedSentenceWords: SharedFlow<Int> = _narratedSentenceWords.asSharedFlow()

    /** Timestamps mot-à-mot de la phrase courante — consommés par le surlignage. */
    private val _currentWordTimestamps = MutableStateFlow<List<WordTimestamp>>(emptyList())
    val currentWordTimestamps: StateFlow<List<WordTimestamp>> = _currentWordTimestamps.asStateFlow()

    /**
     * Intervalle de caractères du mot courant, déduit de la position jouée
     * (Lot 16, Tâche 2.1). `null` hors des bornes de tout mot ou quand la
     * position est invalide — le consommateur (ReaderViewModel) retombe alors
     * sur son repli `delay()`.
     */
    private val _currentWordRange = MutableStateFlow<IntRange?>(null)
    val currentWordRange: StateFlow<IntRange?> = _currentWordRange.asStateFlow()

    /**
     * Vrai quand [AudioPlayer.playbackPosition] est valide. Reflète le flux du
     * lecteur : faux avant le premier échantillon, vrai pendant la lecture,
     * faux à l'arrêt. Consommé par le ReaderViewModel pour choisir entre le
     * surlignage par position et le repli `delay()`.
     */
    private val _positionValid = MutableStateFlow(false)
    val positionValid: StateFlow<Boolean> = _positionValid.asStateFlow()

    /**
     * Fin **naturelle** du chapitre : la dernière phrase a été jouée jusqu'au
     * bout (P1-c/d).
     *
     * Signal dédié, et non plus l'heuristique « `Idle` alors que la lecture
     * était engagée » : cette dernière confondait la fin d'un chapitre avec
     * tout arrêt survenant en cours de lecture — notamment une pause demandée
     * pendant la synthèse (`Buffering`), qui passe par `stop()`. Depuis la
     * notification média, mettre en pause au mauvais instant faisait ainsi
     * sauter un chapitre. Ici, l'émission n'a qu'une seule cause possible.
     */
    private val _chapterCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val chapterCompleted: SharedFlow<Unit> = _chapterCompleted.asSharedFlow()

    /** Métadonnées du livre narré (titre/auteur) — posées par le Lecteur, lues par la notification. */
    private val _metadata = MutableStateFlow(PlaybackMetadata())
    override val metadata: StateFlow<PlaybackMetadata> = _metadata.asStateFlow()

    /**
     * Vrai quand l'audio est engagé (buffering ou lecture réelle). Dérivé de
     * [state] — jamais un second drapeau maintenu à la main (source classique
     * de désynchronisation entre l'écran et la notification).
     */
    override val isPlaying: StateFlow<Boolean> = _state
        .map { it == PlaybackStatus.Playing || it == PlaybackStatus.Buffering }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * État de vie complet de la session (contrat [PlaybackSession]) — miroir
     * domaine du [PlaybackStatus] interne, pour que la notification puisse
     * distinguer une pause réelle (`PAUSED`) d'un arrêt (`IDLE`).
     */
    override val sessionState: StateFlow<PlaybackSessionState> = _state
        .map { it.toSessionState() }
        .stateIn(scope, SharingStarted.Eagerly, PlaybackSessionState.IDLE)

    /**
     * Contexte de la dernière session démarrée (phrases, voix, publication),
     * retenu pour que la notification puisse reprendre/sauter une phrase sans
     * repasser par le Lecteur. `null` tant qu'aucune lecture n'a été lancée.
     */
    private var session: SessionContext? = null

    /**
     * Programme de narration courant (P2-b). `null` tant que le Lecteur n'en a
     * pas posé : l'auto-avance est alors inactive et la narration s'arrête en
     * fin de chapitre, comportement d'avant ce palier.
     */
    private var program: NarrationProgram? = null

    private val playGeneration = AtomicLong(0)
    private var playbackJob: Job? = null
    private var wordTrackingJob: Job? = null

    /**
     * Libération différée des ressources de lecture (P2-b), posée par le
     * Lecteur quand il se ferme alors qu'une narration continue sans lui.
     *
     * Ne capture que des objets de session (résolveur EPUB, parseur,
     * identifiant de publication) — jamais le ViewModel, qui doit rester
     * collectable.
     */
    private var pendingSessionRelease: (() -> Unit)? = null

    init {
        // Miroir de la validité de la position du lecteur, sans verrou ni
        // contention (simple collecte d'un StateFlow) — c'est le signal qui
        // fait basculer le ReaderViewModel vers le repli `delay()`.
        scope.launch {
            audioPlayer.playbackPosition.collect { position ->
                _positionValid.value = position.valid
            }
        }

        // P2-b — fin RÉELLE de la narration : libère les ressources que le
        // Lecteur nous a laissées. `collectLatest` fait tout le travail
        // délicat : l'`Idle` transitoire d'une auto-avance de chapitre annule
        // le délai dès que l'état repasse à `Buffering`, si bien qu'un seul
        // point couvre tous les chemins de fin (arrêt depuis la notification,
        // fin du livre, chapitre illisible) sans distinguer l'arrêt volontaire
        // du `stop()` interne que `play()` déclenche à chaque relance.
        scope.launch {
            state.collectLatest { status ->
                if (status is PlaybackStatus.Idle || status is PlaybackStatus.Error) {
                    delay(SESSION_RELEASE_DEBOUNCE_MS)
                    runPendingSessionRelease()
                }
            }
        }
    }

    /**
     * Enregistre la libération à exécuter quand la narration s'arrêtera pour de
     * bon. Remplace toute libération déjà en attente (une seule session à la
     * fois) ; `null` l'annule — cas d'un Lecteur rouvert sur le même livre, qui
     * reprend la propriété de ses ressources.
     */
    fun releaseOnSessionEnd(release: (() -> Unit)?) {
        pendingSessionRelease = release
    }

    private fun runPendingSessionRelease() {
        val release = pendingSessionRelease ?: return
        pendingSessionRelease = null
        program = null
        release()
    }

    /** Sérialise pause/resume/stop (appelés depuis UI, MediaSession, etc.). */
    private val stateLock = ReentrantLock()

    /**
     * Lance la lecture gapless à partir de [startFrom]. Requiert `startFrom`
     * dans les bornes de [sentences] (garanti par l'appelant).
     */
    fun play(
        sentences: List<Sentence>,
        voiceProfile: VoiceProfile,
        startFrom: Int,
        publicationId: String,
        chapterIndex: Int,
        resourceHref: String,
    ) {
        if (sentences.isEmpty()) return
        session = SessionContext(sentences, voiceProfile, publicationId, chapterIndex, resourceHref)
        stop()
        val generation = playGeneration.incrementAndGet()
        _currentSentenceIndex.value = startFrom
        _currentChapterIndex.value = chapterIndex
        _state.value = PlaybackStatus.Buffering
        playbackJob = scope.launch {
            run(generation, sentences, voiceProfile, startFrom, publicationId, chapterIndex, resourceHref)
        }
    }

    /** Suspend la lecture sans vider la file ni perdre la position. */
    override fun pause() {
        stateLock.lock()
        try {
            if (_state.value != PlaybackStatus.Playing) return
            _state.value = PlaybackStatus.Paused
            audioPlayer.pause()
        } finally {
            stateLock.unlock()
        }
    }

    /** Reprend la lecture après [pause]. */
    override fun resume() {
        stateLock.lock()
        try {
            if (_state.value != PlaybackStatus.Paused) return
            _state.value = PlaybackStatus.Playing
            audioPlayer.resume()
        } finally {
            stateLock.unlock()
        }
    }

    /** Arrête la lecture, vide la file du lecteur et invalide la génération courante. */
    override fun stop() {
        stateLock.lock()
        try {
            playGeneration.incrementAndGet()
            playbackJob?.cancel()
            playbackJob = null
            wordTrackingJob?.cancel()
            wordTrackingJob = null
            audioPlayer.stop()
            _currentWordRange.value = null
            _positionValid.value = false
            _state.value = PlaybackStatus.Idle
        } finally {
            stateLock.unlock()
        }
    }

    /**
     * Vrai quand une session de lecture est engagée — en cours, en synthèse,
     * ou en pause réelle (P1-d).
     *
     * Lit l'état interne [_state] et non le flux dérivé [sessionState] : ce
     * dernier est un `stateIn` alimenté par une coroutine, donc périmé d'un
     * tick après une mutation. Pour une décision immédiate (« faut-il couper
     * la voix en détruisant l'écran ? »), seule la source directe convient.
     *
     * Une pause compte comme engagée : la notification est toujours affichée
     * et propose la reprise, la couper serait un arrêt déguisé.
     */
    fun isSessionEngaged(): Boolean = when (_state.value) {
        PlaybackStatus.Playing, PlaybackStatus.Buffering, PlaybackStatus.Paused -> true
        PlaybackStatus.Idle, is PlaybackStatus.Error -> false
    }

    // ── Minuteur de sommeil (P2-b, écart 4 de P1) ──────────────────────

    private val _sleepTimer = MutableStateFlow<SleepTimerState?>(null)
    override val sleepTimer: StateFlow<SleepTimerState?> = _sleepTimer.asStateFlow()

    private var sleepTimerJob: Job? = null

    /**
     * Arme le minuteur de sommeil, ou l'annule si [minutes] est `null`.
     *
     * Vit ici plutôt que dans le Lecteur (où il était jusqu'à ce palier) parce
     * que s'endormir en écoutant est exactement le cas où l'écran de lecture
     * n'existe plus : un minuteur attaché à l'écran laissait la narration
     * tourner toute la nuit dès qu'on quittait le Lecteur.
     *
     * Décompte à la seconde plutôt qu'un unique `delay` : la notification doit
     * pouvoir afficher le temps restant, ce qu'une simple attente ne permet
     * pas. À l'expiration, `stop()` — jamais une simple mise à faux d'un
     * drapeau, qui laisserait la phrase en cours finir de jouer.
     */
    override fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes == null || minutes <= 0) {
            _sleepTimer.value = null
            return
        }
        val totalMs = minutes * 60_000L
        _sleepTimer.value = SleepTimerState(remainingMs = totalMs, totalMs = totalMs)
        sleepTimerJob = scope.launch {
            var remaining = totalMs
            while (remaining > 0) {
                delay(SLEEP_TIMER_TICK_MS)
                remaining -= SLEEP_TIMER_TICK_MS
                _sleepTimer.value = SleepTimerState(
                    remainingMs = remaining.coerceAtLeast(0),
                    totalMs = totalMs,
                )
            }
            _sleepTimer.value = null
            stop()
        }
    }

    /**
     * Pose le programme de narration (P2-b) : la suite ordonnée des hrefs de
     * chapitres du livre ouvert. Appelé par le Lecteur à l'ouverture ; c'est ce
     * qui autorise l'auto-avance à survivre à la destruction de l'écran.
     *
     * Sans appel, [program] reste `null` et la narration s'arrête en fin de
     * chapitre — jamais de comportement à moitié câblé.
     */
    fun setNarrationProgram(publicationId: String, chapterHrefs: List<String>, skipEmptyChapters: Boolean = false) {
        program = NarrationProgram(publicationId, chapterHrefs, skipEmptyChapters)
    }

    /**
     * Retire le programme : l'auto-avance redevient inactive. Appelé quand le
     * Lecteur se ferme **sans** session engagée — sinon le programme doit
     * survivre, c'est tout l'objet du palier.
     */
    fun clearNarrationProgram() {
        program = null
    }

    /**
     * Pose les métadonnées (titre/auteur) du livre narré. Appelé par le
     * Lecteur à l'ouverture, consommé par la notification média.
     */
    fun setMetadata(publicationId: String?, title: String?, author: String?, coverUri: String? = null) {
        _metadata.value = PlaybackMetadata(
            publicationId = publicationId,
            title = title,
            author = author,
            coverUri = coverUri,
        )
    }

    /**
     * Démarrage à froid d'une narration (contrat [PlaybackSession]) — depuis
     * la carte « Reprendre la lecture » de la Bibliothèque, sans ouvrir le
     * Lecteur.
     *
     * Refait ici, sans écran, ce que `ReaderViewModel.openPublication` +
     * `playCurrentSentence` font avec lui : résoudre la publication, son
     * spine, sa position de reprise, sa voix, puis lancer la lecture. Le
     * service foreground et sa notification suivent tout seuls (
     * `PlaybackServiceLauncher` observe [sessionState]) — rien à déclencher
     * ici.
     *
     * Deux points de vigilance, tous deux des bugs déjà payés ailleurs :
     * - l'index de chapitre qui fait foi est la **position dans le spine**
     *   (`chapters`), jamais `Chapter.index` recalculé par le parseur, qui
     *   diverge sur les livres à couverture prépendue (même correction que
     *   `loadChapterContentIfNeeded` et [advanceToNextChapter]) ;
     * - la phrase de départ vient du `charOffset` du `Locator` restauré, pas
     *   d'un départ implicite à zéro (K3).
     */
    override fun startNarration(publicationId: String) {
        scope.launch {
            val publication = publicationRepository.getById(publicationId) ?: return@launch
            // Préchauffe comme le fait le Lecteur à l'ouverture : dans un
            // process neuf, l'init froide des modèles ONNX dépasse le timeout
            // de synthèse et le moteur retomberait sur la voix système.
            ttsEngine.warmUp()

            val chapters = when (val result = publicationParser.parse(publication.fileUri)) {
                is ParseResult.Success -> result.documentModel.chapters
                // K6/K7 : un livre corrompu, protégé ou illisible ne fait pas
                // planter l'app — la narration ne démarre simplement pas. Le
                // message d'erreur détaillé reste l'affaire du Lecteur, qui
                // sait l'afficher ; ici il n'y a pas d'écran pour le porter.
                else -> return@launch
            }
            if (chapters.isEmpty()) return@launch
            chapterParser.registerPublication(publicationId, publication.fileUri)

            val restored = getReadingState(publicationId)
            val isPdf = publication.format == PublicationFormat.PDF
            val chapterIndex = (restored?.locator?.chapterIndex ?: 0).coerceIn(chapters.indices)
            val chapter = chapters[chapterIndex]

            // EPUB : contenu paresseux, `sentences` est vide tant que le
            // chapitre n'a pas été parsé. TXT/PDF : déjà porté par le parse
            // complet ci-dessus (même distinction que
            // `loadChapterContentIfNeeded`, qui ignore les chapitres Legacy).
            val sentences = chapter.sentences.ifEmpty {
                try {
                    chapterParser.parseChapter(publicationId, chapter.href).sentences
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@launch
                }
            }
            // Une page PDF sans texte (planche scannee) n'est pas une erreur :
            // `advanceToNextChapter` traversera jusqu'a la prochaine page
            // narrable. Un chapitre EPUB vide, lui, reste un echec.
            if (sentences.isEmpty() && !isPdf) return@launch

            val charOffset = restored?.locator?.charOffset ?: 0
            val startFrom = sentences
                .indexOfFirst { charOffset in it.startOffset..it.endOffset }
                .coerceAtLeast(0)

            val prefs = preferencesRepository.get()
            val voiceProfile = prefs.activeVoiceProfileId
                ?.let { voiceProfileRepository.getById(it) }
                ?: DEFAULT_NARRATION_VOICE_PROFILE

            setMetadata(
                publicationId = publication.id,
                title = publication.title.cleanedForDisplay(),
                author = publication.authors.cleanedAuthorsForDisplay().ifBlank { null },
                coverUri = publication.coverUri,
            )
            setNarrationProgram(publicationId, chapters.map { it.href }, skipEmptyChapters = isPdf)
            play(
                sentences = sentences,
                voiceProfile = voiceProfile,
                startFrom = startFrom,
                publicationId = publicationId,
                chapterIndex = chapterIndex,
                resourceHref = chapter.href,
            )
        }
    }

    /**
     * Bascule lecture ↔ pause (contrat [PlaybackSession], P1). La pause est
     * une **vraie pause** (`pause()`, état `Paused`) — distincte de l'arrêt
     * `stop()` que l'écran Lecteur emploie pour « mettre en pause ». Reprend
     * (ou relance) depuis la phrase courante après un arrêt.
     */
    override fun togglePlayPause() {
        when (_state.value) {
            PlaybackStatus.Playing -> pause()
            PlaybackStatus.Paused -> resume()
            // Buffering : rien d'audible encore, « pause » = annuler la
            // synthèse en cours (retour Idle, sans reprise possible).
            // Cet `Idle` ne déclenche plus d'auto-avance : la fin de chapitre
            // a désormais son signal propre ([chapterCompleted]), émis par le
            // seul chemin qui la constate réellement.
            PlaybackStatus.Buffering -> stop()
            PlaybackStatus.Idle -> restart()
            is PlaybackStatus.Error -> restart()
        }
    }

    /**
     * Recule/avance d'une phrase (contrat [PlaybackSession], P1). Reprend
     * immédiatement si la lecture était engagée ; sinon se contente de
     * déplacer l'index (la position sera persistée au prochain [play]).
     */
    override fun skip(delta: Int) {
        val ctx = session ?: return
        val wasPlaying = _state.value == PlaybackStatus.Playing || _state.value == PlaybackStatus.Buffering
        val newIndex = (_currentSentenceIndex.value + delta).coerceIn(0, ctx.sentences.lastIndex)
        if (wasPlaying) {
            play(ctx.sentences, ctx.voiceProfile, newIndex, ctx.publicationId, ctx.chapterIndex, ctx.resourceHref)
        } else {
            _currentSentenceIndex.value = newIndex
        }
    }

    /** Relance la lecture depuis la session retenue, à la phrase courante. */
    private fun restart() {
        val ctx = session ?: return
        play(ctx.sentences, ctx.voiceProfile, _currentSentenceIndex.value, ctx.publicationId, ctx.chapterIndex, ctx.resourceHref)
    }

    private suspend fun run(
        generation: Long,
        sentences: List<Sentence>,
        voiceProfile: VoiceProfile,
        startFrom: Int,
        publicationId: String,
        chapterIndex: Int,
        resourceHref: String,
    ) {
        val rulesHash = computeRulesHash()
        val channel = Channel<AudioSegment>(LOOKAHEAD)
        val producer = scope.launch {
            produce(generation, channel, sentences, voiceProfile, startFrom, publicationId, chapterIndex, rulesHash)
        }
        try {
            consume(generation, channel, sentences, startFrom, publicationId, chapterIndex, resourceHref, voiceProfile, rulesHash)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isCurrent(generation)) {
                _state.value = PlaybackStatus.Error(e.message ?: "Erreur de lecture")
            }
        } finally {
            producer.cancel()
            channel.close()
        }
    }

    /** Producteur : synthétise dans l'ordre, timeout unique, silence court sur erreur. */
    private suspend fun produce(
        generation: Long,
        channel: Channel<AudioSegment>,
        sentences: List<Sentence>,
        voiceProfile: VoiceProfile,
        startFrom: Int,
        publicationId: String,
        chapterIndex: Int,
        rulesHash: String,
    ) {
        try {
            for (i in startFrom until sentences.size) {
                if (!isCurrent(generation)) break
                val sentence = sentences[i]
                val key = ttsCacheKey(
                    publicationId = publicationId,
                    chapterIndex = chapterIndex,
                    sentenceOffset = sentence.startOffset,
                    voiceProfileId = voiceProfile.id,
                    pronunciationRulesHash = rulesHash,
                    engineVersion = ttsEngine.synthesisVersion,
                )
                // Lot 22, Palier B — consultation AVANT synthèse, écriture
                // après. Le LOOKAHEAD=3 (acquis du Lot 15) masque déjà la
                // latence en lecture linéaire : le gain réel est le
                // démarrage à froid, la reprise et le retour en arrière.
                val cached = ttsSegmentCache.get(publicationId, key)
                val segment = if (cached != null) {
                    cached
                } else {
                    var synthesized = false
                    val fresh = try {
                        val s = withTimeout(SYNTHESIS_TIMEOUT_MS) { ttsEngine.synthesize(sentence, voiceProfile) }
                        synthesized = true
                        s
                    } catch (e: TimeoutCancellationException) {
                        silence(TIMEOUT_SILENCE_MS, audioPlayer.sampleRate)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        silence(TIMEOUT_SILENCE_MS, audioPlayer.sampleRate)
                    }
                    // Seule une synthèse RÉUSSIE est cachée — jamais un
                    // silence de secours (le rejouer serait du faux texte).
                    if (synthesized) ttsSegmentCache.put(publicationId, key, fresh)
                    fresh
                }
                if (!isCurrent(generation)) break
                channel.send(segment)
            }
        } finally {
            channel.close()
        }
    }

    /**
     * Consommateur : enfile segment + silence, `play()` au premier, et pace
     * l'index de phrase sur une **timeline absolue** (cumul des durées depuis
     * le `play()`), indépendamment du temps passé à attendre la synthèse.
     * Chaque segment suivant est enfilé dès qu'il arrive (le producteur court
     * à LOOKAHEAD) pour rester gapless.
     */
    private suspend fun consume(
        generation: Long,
        channel: Channel<AudioSegment>,
        sentences: List<Sentence>,
        startFrom: Int,
        publicationId: String,
        chapterIndex: Int,
        resourceHref: String,
        voiceProfile: VoiceProfile,
        rulesHash: String,
    ) {
        var index = startFrom
        var playStartNanos = 0L
        var nextPhraseStartMs = 0L
        var first = true

        for (segment in channel) {
            if (!isCurrent(generation)) return
            val sentence = sentences.getOrNull(index)
            val silenceMs = silenceDurationFor(sentence?.text ?: "")
            val phraseDurationMs = segment.durationMs + silenceMs
            // Position jouée au début de l'audio de cette phrase (cumul des
            // durées précédentes) — repère du surlignage mot par position.
            val sentenceStartMs = nextPhraseStartMs

            audioPlayer.sampleRate = segment.sampleRate
            audioPlayer.enqueue(segment)
            audioPlayer.enqueue(silence(silenceMs, segment.sampleRate))

            if (first) {
                audioPlayer.play()
                _state.value = PlaybackStatus.Playing
                playStartNanos = System.nanoTime()
                advanceTo(generation, index, publicationId, chapterIndex, resourceHref, sentence, segment.wordTimestamps, voiceProfile, rulesHash)
                first = false
            } else {
                // Pace jusqu'au début ABSOLU de cette phrase (cumul des durées),
                // pas un délai relatif : le temps passé à attendre la synthèse
                // de cette phrase ne doit pas décaler le surlignage.
                val elapsedMs = (System.nanoTime() - playStartNanos) / 1_000_000L
                val waitMs = nextPhraseStartMs - elapsedMs
                if (waitMs > 0) pace(generation, waitMs)
                // Le pace ci-dessus s'achève quand l'audio de la phrase
                // précédente est écoulé : c'est là, et pas à son démarrage,
                // qu'elle est acquise pour les statistiques.
                creditNarratedSentence(sentences.getOrNull(index - 1))
                advanceTo(generation, index, publicationId, chapterIndex, resourceHref, sentence, segment.wordTimestamps, voiceProfile, rulesHash)
            }
            launchWordTracking(generation, sentenceStartMs, segment.wordTimestamps)
            nextPhraseStartMs += phraseDurationMs
            index++
        }

        if (isCurrent(generation)) {
            if (!first) {
                val elapsedMs = (System.nanoTime() - playStartNanos) / 1_000_000L
                val waitMs = nextPhraseStartMs - elapsedMs
                if (waitMs > 0) pace(generation, waitMs)
                // Dernière phrase du chapitre : aucune transition ne viendra
                // plus la créditer, c'est cette attente-ci qui la termine.
                creditNarratedSentence(sentences.getOrNull(index - 1))
            }
            _state.value = PlaybackStatus.Idle
            // Après l'`Idle` : l'écran doit d'abord enregistrer la fin de la
            // lecture courante, puis seulement enchaîner le chapitre suivant.
            _chapterCompleted.tryEmit(Unit)
            advanceToNextChapter(generation, chapterIndex)
        }
    }

    /**
     * Enchaîne le chapitre suivant du [program] (P2-b), phrases obtenues de
     * [ChapterParser] plutôt que de l'écran — c'est ce qui rend l'auto-avance
     * indépendante de la vie du Lecteur.
     *
     * Repasse en `Buffering` **avant** de parser : le parsing peut prendre
     * plusieurs centaines de millisecondes, et laisser l'état à `Idle` pendant
     * ce temps ferait retirer la notification puis la reposer (le service
     * foreground suit `sessionState`) — un clignotement visible, et un
     * arrêt/redémarrage de service pour rien.
     *
     * Toute impasse (fin du livre, href introuvable, chapitre sans phrase)
     * laisse simplement la narration terminée : jamais de saut silencieux vers
     * un chapitre plus loin, qui ferait perdre du texte à l'auditeur.
     */
    private suspend fun advanceToNextChapter(generation: Long, completedChapterIndex: Int) {
        val prog = program ?: return
        val ctx = session ?: return
        if (!isCurrent(generation)) return

        _state.value = PlaybackStatus.Buffering

        // Sur un PDF, on traverse les pages sans texte jusqu'a la prochaine
        // page narrable ([NarrationProgram.skipEmptyChapters]) ; sur un EPUB,
        // la boucle s'arrete des le premier chapitre (une seule iteration) et
        // le comportement est strictement celui d'avant.
        var nextIndex = completedChapterIndex + 1
        var skipped = 0
        var chapter: Chapter
        var nextHref: String
        while (true) {
            nextHref = prog.chapterHrefs.getOrNull(nextIndex) ?: return // fin du livre

            chapter = try {
                chapterParser.parseChapter(prog.publicationId, nextHref)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Même politique que le Lecteur (K6/K7) : un href introuvable ou un
                // chapitre malformé n'interrompt pas l'app, il termine la narration.
                _state.value = PlaybackStatus.Idle
                return
            }
            // Une pause ou un arrêt survenu pendant le parsing a péremé la
            // génération : ne pas relancer une narration que l'utilisateur vient
            // d'interrompre.
            if (!isCurrent(generation)) return
            if (chapter.sentences.isNotEmpty()) break

            if (!prog.skipEmptyChapters || skipped >= MAX_EMPTY_CHAPTER_SKIPS) {
                _state.value = PlaybackStatus.Idle
                return
            }
            skipped++
            nextIndex++
        }
        // `chapter.index` recalculé par le parseur peut diverger de la position
        // réelle dans le spine (livres à couverture prépendue — même bug que
        // `loadChapterContentIfNeeded`). La position dans le programme fait foi.
        play(
            sentences = chapter.sentences,
            voiceProfile = ctx.voiceProfile,
            startFrom = 0,
            publicationId = prog.publicationId,
            chapterIndex = nextIndex,
            resourceHref = nextHref,
        )
    }

    /**
     * Crédite une phrase entièrement prononcée sur [narratedSentenceWords].
     * `null` ou phrase vide : rien à créditer, silencieusement.
     */
    private fun creditNarratedSentence(sentence: Sentence?) {
        val words = sentence?.wordCount ?: return
        if (words > 0) _narratedSentenceWords.tryEmit(words)
    }

    /**
     * Avance l'index de phrase courant et persiste la position de reprise
     * (progression à la phrase — jamais au mot, écart déclaré).
     */
    private suspend fun advanceTo(
        generation: Long,
        index: Int,
        publicationId: String,
        chapterIndex: Int,
        resourceHref: String,
        sentence: Sentence?,
        wordTimestamps: List<WordTimestamp>,
        voiceProfile: VoiceProfile,
        rulesHash: String,
    ) {
        if (!isCurrent(generation)) return
        _currentSentenceIndex.value = index
        _currentWordTimestamps.value = wordTimestamps
        if (publicationId.isNotEmpty() && sentence != null) {
            // Bug réel trouvé au diagnostic (même famille que ReaderViewModel
            // .persistPosition) : construire un ReadingState « nu » ici
            // écrasait silencieusement overrides/voiceProfileId déjà
            // enregistrés à CHAQUE transition de phrase narrée — préserver
            // l'existant, comme le chemin manuel le fait déjà.
            val existing = getReadingState(publicationId)
            updateReadingState(
                ReadingState(
                    publicationId = publicationId,
                    locator = sentence.startLocator(chapterIndex = chapterIndex, resourceHref = resourceHref),
                    lastReadAt = System.currentTimeMillis(),
                    voiceProfileId = existing?.voiceProfileId,
                    overrides = existing?.overrides,
                ),
            )
            // Lot 22, Palier B, décision 4 — épingle le segment de la
            // position de reprise : c'est le cœur du gain, le tap sur
            // « reprendre » doit démarrer sans synthèse. Épinglé à chaque
            // transition, le pin suit la position persistée ci-dessus.
            ttsSegmentCache.pinResumePoint(
                publicationId,
                ttsCacheKey(
                    publicationId = publicationId,
                    chapterIndex = chapterIndex,
                    sentenceOffset = sentence.startOffset,
                    voiceProfileId = voiceProfile.id,
                    pronunciationRulesHash = rulesHash,
                    engineVersion = ttsEngine.synthesisVersion,
                ),
            )
        }
    }

    /** Hash stable des règles de prononciation actives — dimension d'invalidation du cache TTS. */
    private suspend fun computeRulesHash(): String =
        pronunciationRuleRepository.observeAll().first()
            .filter { it.isEnabled }
            .sortedBy { it.id }
            .joinToString("|") { "${it.originalText}\u0000${it.replacementText}\u0000${it.isRegex}" }
            .let(::sha256Hex)

    /**
     * Attend [durationMs] par pas courts, en suspendant pendant une pause et
     * en s'interrompant si la génération est périmée. Approximatif par nature
     * (durées cumulées, pas position AudioTrack) — la synchronisation « par
     * position réelle » est reportée au LOT 16.
     */
    private suspend fun pace(generation: Long, durationMs: Long) {
        var remaining = durationMs
        while (remaining > 0 && isCurrent(generation)) {
            if (_state.value == PlaybackStatus.Paused) {
                delay(PACE_STEP_MS)
                continue
            }
            val step = minOf(remaining, PACE_STEP_MS)
            delay(step)
            remaining -= step
        }
    }

    /**
     * Surlignage mot par position (Lot 16, Tâche 2.1) : un coroutine par
     * phrase déduit [wordRangeAt] de la position jouée et met à jour
     * [_currentWordRange]. Si la position est invalide, aucune plage n'est
     * émise (le consommateur retombe sur le repli `delay()`). Sort dès que la
     * position dépasse le dernier mot, ou que la génération est périmée.
     */
    private fun launchWordTracking(generation: Long, sentenceStartMs: Long, wordTimestamps: List<WordTimestamp>) {
        wordTrackingJob?.cancel()
        _currentWordRange.value = null
        if (wordTimestamps.isEmpty()) return
        val lastEndMs = sentenceStartMs + wordTimestamps.last().endMs
        wordTrackingJob = scope.launch {
            while (isCurrent(generation)) {
                if (_state.value == PlaybackStatus.Paused) {
                    delay(WORD_TRACKING_STEP_MS)
                    continue
                }
                val position = audioPlayer.playbackPosition.value
                val range = if (position.valid) {
                    wordRangeAt(position.playedMs, sentenceStartMs, wordTimestamps)
                } else {
                    null
                }
                if (_currentWordRange.value != range) {
                    _currentWordRange.value = range
                }
                if (position.valid && position.playedMs >= lastEndMs) return@launch
                delay(WORD_TRACKING_STEP_MS)
            }
        }
    }

    private fun isCurrent(generation: Long): Boolean = playGeneration.get() == generation

    /** Génère un silence PCM16 de [durationMs] au [sampleRate] donné. */
    private fun silence(durationMs: Long, sampleRate: Int): AudioSegment {
        val frameCount = (sampleRate * durationMs / 1000L).toInt().coerceAtLeast(1)
        return AudioSegment(
            audioData = ByteArray(frameCount * 2),
            durationMs = durationMs,
            wordTimestamps = emptyList(),
            sampleRate = sampleRate,
        )
    }

    private fun silenceDurationFor(text: String): Long {
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return SILENCE_SENTENCE_MS
        return when (trimmed.last()) {
            ',', ';' -> SILENCE_COMMA_MS
            '.', '!', '?', '\u2026' -> SILENCE_SENTENCE_MS
            '\n' -> SILENCE_PARAGRAPH_MS
            else -> SILENCE_SENTENCE_MS
        }
    }

    /** Mappe le statut interne vers l'état domaine du contrat [PlaybackSession]. */
    private fun PlaybackStatus.toSessionState(): PlaybackSessionState = when (this) {
        PlaybackStatus.Idle -> PlaybackSessionState.IDLE
        PlaybackStatus.Buffering -> PlaybackSessionState.BUFFERING
        PlaybackStatus.Playing -> PlaybackSessionState.PLAYING
        PlaybackStatus.Paused -> PlaybackSessionState.PAUSED
        is PlaybackStatus.Error -> PlaybackSessionState.ERROR
    }

    /** Contexte d'une session de lecture retenu pour la reprise/skip sans le Lecteur. */
    private data class SessionContext(
        val sentences: List<Sentence>,
        val voiceProfile: VoiceProfile,
        val publicationId: String,
        val chapterIndex: Int,
        val resourceHref: String,
    )

    private companion object {
        /** Pages sans texte traversees avant de renoncer (PDF entierement scanne). */
        const val MAX_EMPTY_CHAPTER_SKIPS = 20

        const val LOOKAHEAD = 3
        /** Timeout de synthèse unique (Edge cloud et Sherpa-ONNX confondus). */
        const val SYNTHESIS_TIMEOUT_MS = 20_000L
        /** Silence injecté après une synthèse en échec. */
        const val TIMEOUT_SILENCE_MS = 50L
        const val PACE_STEP_MS = 50L
        const val WORD_TRACKING_STEP_MS = 20L
        const val SILENCE_COMMA_MS = 150L
        const val SILENCE_SENTENCE_MS = 650L
        const val SILENCE_PARAGRAPH_MS = 1000L

        /**
         * Délai avant de libérer les ressources de lecture sur un état terminal
         * (P2-b). Doit couvrir largement l'`Idle` transitoire d'une auto-avance
         * — le parsing du chapitre suivant repasse en `Buffering` bien avant.
         */
        const val SESSION_RELEASE_DEBOUNCE_MS = 1_500L

        /** Pas de décompte du minuteur de sommeil (affichage à la seconde). */
        const val SLEEP_TIMER_TICK_MS = 1_000L
    }
}

/**
 * Repli de voix pour un démarrage à froid ([PlaybackOrchestrator.startNarration])
 * quand `UserPreferences.activeVoiceProfileId` est nul — même profil que le
 * repli du Lecteur (`ReaderViewModel.resolveVoiceProfile`).
 */
private val DEFAULT_NARRATION_VOICE_PROFILE = VoiceProfile(
    id = "vp-native-fr", engine = TtsEngineId.ANDROID_NATIVE,
    voice = "fr-fr-default", language = "fr-FR",
)
