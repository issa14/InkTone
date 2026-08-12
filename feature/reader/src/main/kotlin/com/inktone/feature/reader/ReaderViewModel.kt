package com.inktone.feature.reader

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Bookmark
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.EffectiveReadingSettings
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingMode as DomainReadingMode
import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingSession
import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.RenderedPage
import com.inktone.domain.model.SleepTimerState
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.repository.AnnotationRepository
import com.inktone.domain.repository.BookmarkRepository
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingSessionRepository
import com.inktone.domain.repository.ThemeRepository
import com.inktone.domain.repository.VoiceProfileRepository
import com.inktone.domain.service.ChapterParser
import com.inktone.domain.service.EpubResourceResolver
import com.inktone.domain.service.FixedPageDocument
import com.inktone.domain.service.FixedPageOpenResult
import com.inktone.domain.service.FixedPageRenderer
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationParser
import com.inktone.domain.service.ReadingSessionTracker
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.usecase.AddAnnotationUseCase
import com.inktone.domain.usecase.CreateBookmarkUseCase
import com.inktone.domain.usecase.DeleteBookmarkUseCase
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.GetVoiceProfilesUseCase
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import com.inktone.domain.valueobject.Locator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * MVI complet du Reader (Tâche 4.5) — remplace le squelette à une seule
 * phrase de la Phase 3 par la navigation par chapitre, la TOC et la
 * reprise de position réelle. L'audio est joué via [AudioSegmentPlayer]
 * (AudioTrack, Tâche 3.8) ; AudioPlaybackService (Phase 5) le
 * remplacera pour la lecture en arrière-plan.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val ttsEngine: TtsEngine, // injecte AndroidNativeTtsEngine (Palier 1) via Hilt (infrastructure/tts/di/TtsModule)
    private val audioSegmentPlayer: AudioSegmentPlayer,
    private val publicationParser: PublicationParser, // CompositePublicationParser via Hilt (infrastructure/parser/di/ParserModule)
    private val updateReadingState: UpdateReadingStateUseCase,
    private val getReadingState: GetReadingStateUseCase,
    private val publicationRepository: PublicationRepository,
    private val preferencesRepository: PreferencesRepository,
    private val voiceProfileRepository: VoiceProfileRepository,
    private val getVoiceProfiles: GetVoiceProfilesUseCase,
    private val annotationRepository: AnnotationRepository,
    private val addAnnotation: AddAnnotationUseCase,
    private val bookmarkRepository: BookmarkRepository,
    private val createBookmark: CreateBookmarkUseCase,
    private val deleteBookmark: DeleteBookmarkUseCase,
    // ───── Lot Sessions ─────
    private val readingSessionRepository: ReadingSessionRepository,
    // Lot 9 — résolution id → ReadingTheme complet (couleurs + police).
    private val themeRepository: ThemeRepository,
    // Lot 12, Palier 2 — rendu bitmap PDF (PdfPageRendererImpl via Hilt,
    // infrastructure/parser/di/ParserModule). Jamais le binding PDFium
    // directement (règle de dépendance, Blueprint §4.7).
    private val fixedPageRenderer: FixedPageRenderer,
    // Plan v3, Palier 3.6 — parsing lazy EPUB + résolveur d'images
    private val chapterParser: ChapterParser,
    private val epubResourceResolver: EpubResourceResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        // Tache 9bis.3.6 - reglage global (UserPreferences.readingRulerEnabled,
        // Tache 9bis.5), pas une cascade overrides/preferences comme
        // effectiveSettings : observation continue, independante de toute
        // publication ouverte (meme principe que ImportProgressObserver
        // dans LibraryViewModel).
        viewModelScope.launch {
            preferencesRepository.observe().collect { preferences ->
                _state.value = _state.value.copy(
                    isReadingRulerEnabled = preferences.readingRulerEnabled,
                    lineHeightMultiplier = preferences.lineHeightMultiplier,
                    readerBrightness = preferences.readerBrightness,
                    eyeRestReminderEnabled = preferences.eyeRestReminderEnabled,
                    eyeRestReminderIntervalMinutes = preferences.eyeRestReminderIntervalMinutes,
                    reduceMotion = preferences.reduceMotion,
                )
            }
        }
    }

    // C.5 — exposé pour clé sharedElement dans ReaderScreen
    internal var currentPublicationId: String? = null
    private val chapterPreloader = ChapterPreloader(viewModelScope)
    private val sentenceAudioBuffer = SentenceAudioBuffer(viewModelScope, ttsEngine)
    private val annotationSelectionHandler = AnnotationSelectionHandler()
    private var sleepTimerJob: Job? = null

    // ───── Lot Sessions ─────
    private var sessionTracker: ReadingSessionTracker? = null
    private var checkpointJob: Job? = null
    private var lastFragmentSavedMs: Long = 0L
    // ───── Fin Lot Sessions ─────

    // 3d.5 — rappel de repos oculaire : eyeRestReminderJob porte le délai
    // jusqu'à l'échéance (relancé à chaque reprise, jamais deux en
    // parallèle comme sleepTimerJob) ; eyeRestCountdownJob porte le
    // compte à rebours de 60s du popup une fois affiché ;
    // wasPlayingBeforeEyeRest mémorise s'il faut reprendre le TTS.
    private var eyeRestReminderJob: Job? = null
    private var eyeRestCountdownJob: Job? = null
    private var wasPlayingBeforeEyeRest: Boolean = false

    // A.1bis — job de la coroutine de lecture TTS en cours (une phrase, ou
    // la chaîne auto-avance). Annulé par pausePlayback()/skipSentence() en
    // plus de audioSegmentPlayer.stop() : sans ça, la boucle de surlignage
    // mot-à-mot de playCurrentSentence() continuait d'avancer silencieusement
    // après une pause, seul le son s'arrêtait (bug réel trouvé à l'audit).
    private var playbackJob: Job? = null

    // Lot 4, tâche 4.7 — flash différé : pendingHighlightTimeoutJob est la
    // sortie de secours (mise en page qui n'aboutit jamais) ; flashClearJob
    // efface le flash affiché après un court délai. Un seul de chaque à la
    // fois, même discipline que sleepTimerJob.
    private var pendingHighlightTimeoutJob: Job? = null
    private var flashClearJob: Job? = null

    fun onIntent(intent: ReaderIntent) {
        when (intent) {
            is ReaderIntent.OpenPublication -> {
                val resourceHref = intent.targetResourceHref
                val targetLocator = if (
                    !resourceHref.isNullOrBlank() && intent.targetChapterIndex != null && intent.targetCharOffset != null
                ) {
                    Locator(
                        resourceHref = resourceHref,
                        chapterIndex = intent.targetChapterIndex,
                        charOffset = intent.targetCharOffset,
                    )
                } else {
                    null
                }
                openPublication(intent.publicationId, targetLocator, intent.flashOnArrival)
            }
            is ReaderIntent.NextChapter -> navigateToChapter(_state.value.currentChapterIndex + 1)
            is ReaderIntent.PreviousChapter -> navigateToChapter(_state.value.currentChapterIndex - 1)
            is ReaderIntent.JumpToChapter -> navigateToChapter(intent.chapterIndex)
            is ReaderIntent.ToggleToc -> _state.value = _state.value.copy(isTocVisible = !_state.value.isTocVisible)
            is ReaderIntent.PlayCurrentSentence -> playCurrentSentence()
            is ReaderIntent.Pause -> pausePlayback()
            is ReaderIntent.DismissError -> _state.value = _state.value.copy(errorMessage = null)
            is ReaderIntent.DismissVoiceDownloadPrompt -> _state.value = _state.value.copy(showVoiceDownloadPrompt = false)
            is ReaderIntent.ToggleReadingMode -> {
                // Lot 12, tâche 12.10 — un PDF est nativement paginé,
                // la bascule SCROLL/PAGED n'a pas de sens pour ce format
                // (décision actée 16). Le bouton est déjà masqué dans
                // UnifiedControlPanel (showTtsControls = !isPdf) ; cette
                // garde couvre un déclencheur externe éventuel.
                if (_state.value.publicationFormat == PublicationFormat.PDF) return
                val newMode = if (_state.value.readingMode == ReadingMode.SCROLL) ReadingMode.PAGED else ReadingMode.SCROLL
                _state.value = _state.value.copy(readingMode = newMode)
                // B.1 — persiste le mode de lecture
                viewModelScope.launch {
                    val current = preferencesRepository.get()
                    preferencesRepository.update(current.copy(readingMode = newMode.name))
                }
            }
            is ReaderIntent.SetFreeSelection -> _state.value = _state.value.copy(
                freeSelectionAnchorOffset = intent.anchorOffset, freeSelectionFocusOffset = intent.focusOffset,
            )
            is ReaderIntent.ClearFreeSelection -> _state.value = _state.value.copy(
                freeSelectionAnchorOffset = null, freeSelectionFocusOffset = null,
            )
            is ReaderIntent.ConfirmAnnotation -> confirmAnnotation(intent.color, intent.content)
            is ReaderIntent.ToggleBookmarkAtCurrentPosition -> toggleBookmarkAtCurrentPosition()
            is ReaderIntent.ToggleBookmarkList -> _state.value = _state.value.copy(
                isBookmarkListVisible = !_state.value.isBookmarkListVisible,
            )
            is ReaderIntent.DeleteBookmark -> viewModelScope.launch { deleteBookmark(intent.id) }
            is ReaderIntent.NavigateToLocator -> navigateToLocator(intent.locator)
            is ReaderIntent.ChapterLayoutCompleted -> onChapterLayoutCompleted(intent.chapterIndex)
            is ReaderIntent.SetOverrides -> setOverrides(intent.overrides)
            is ReaderIntent.SetSleepTimer -> setSleepTimer(intent.minutes)
            is ReaderIntent.SkipToPreviousSentence -> skipSentence(-1)
            is ReaderIntent.SkipToNextSentence -> skipSentence(1)
            is ReaderIntent.UpdateScrollPosition -> updateScrollPosition(intent.sentenceIndex)
            is ReaderIntent.SetTtsSpeed -> setTtsSpeed(intent.speed)
            is ReaderIntent.SetActiveVoiceProfile -> setActiveVoiceProfile(intent.profileId)
            is ReaderIntent.SetLineHeight -> setLineHeight(intent.multiplier)
            is ReaderIntent.SetReaderBrightness -> setReaderBrightness(intent.value)
            is ReaderIntent.SetEyeRestReminderEnabled -> setEyeRestReminderEnabled(intent.enabled)
            is ReaderIntent.SetEyeRestReminderInterval -> setEyeRestReminderInterval(intent.minutes)
            is ReaderIntent.ResumeFromEyeRestReminder -> resumeFromEyeRestReminder()
            is ReaderIntent.SnoozeEyeRestReminder -> snoozeEyeRestReminder()
            is ReaderIntent.UpdatePageOffset -> updatePageOffset(intent.offsetY)
            is ReaderIntent.ToggleForcePdfInversion -> _state.value = _state.value.copy(
                forcePdfInversion = !_state.value.forcePdfInversion,
            )
        }
    }

    private var scrollPersistJob: Job? = null

    // Lot 12, Palier 2 (tache 12.9) — cycle de vie distinct du parsing
    // (decision actee 14 du plan) : ouvert une fois a l'ouverture d'une
    // publication PDF, ferme explicitement a la fermeture de celle-ci ou
    // du ViewModel (onCleared), jamais rouvert par page.
    private var fixedPageDocument: FixedPageDocument? = null
    private var pageOffsetPersistJob: Job? = null

    /**
     * Tâche 3c.1 — antipattern legacy corrigé : la position de lecture en
     * défilement silencieux (sans TTS) n'était jamais persistée avant ce
     * lot. Écrit `currentSentenceIndex` immédiatement (pour que le
     * pourcentage/la page dérivés dans `ReaderUiState`/`ReaderScreen`
     * restent cohérents pendant le geste), mais débounce la persistance en
     * base — un défilement rapide traverse potentiellement des dizaines de
     * phrases par seconde, écrire à chaque changement d'index saturerait
     * Room pour une position qui n'a d'intérêt qu'une fois le défilement
     * stabilisé.
     *
     * Ignoré pendant le TTS (K3, chemins manuel et TTS jamais simultanés) :
     * `playCurrentSentence` avance déjà `currentSentenceIndex` et persiste
     * sa propre position, un second écrivain concurrent créerait la
     * divergence que K3 interdit.
     */
    private fun updateScrollPosition(sentenceIndex: Int) {
        if (_state.value.isPlaying) return
        if (sentenceIndex == _state.value.currentSentenceIndex) return
        val chapterIndex = _state.value.currentChapterIndex
        _state.value = _state.value.copy(currentSentenceIndex = sentenceIndex)
        scrollPersistJob?.cancel()
        scrollPersistJob = viewModelScope.launch {
            delay(SCROLL_PERSIST_DEBOUNCE_MS)
            persistPosition(chapterIndex = chapterIndex, sentenceIndex = sentenceIndex)
        }
    }

    /**
     * Lot 12, tache 12.9 — miroir de [updateScrollPosition] pour le
     * format PDF, meme debounce (persister a chaque frame d'un geste de
     * panoramique saturerait Room pour une position qui n'interesse
     * qu'une fois le geste stabilise).
     */
    private fun updatePageOffset(offsetY: Float) {
        if (offsetY == _state.value.pageOffsetY) return
        val chapterIndex = _state.value.currentChapterIndex
        _state.value = _state.value.copy(pageOffsetY = offsetY)
        pageOffsetPersistJob?.cancel()
        pageOffsetPersistJob = viewModelScope.launch {
            delay(SCROLL_PERSIST_DEBOUNCE_MS)
            persistPosition(chapterIndex = chapterIndex, sentenceIndex = 0)
        }
    }

    /**
     * Lot 12, tache 12.9 — enveloppe [FixedPageDocument.renderPage] pour
     * `FixedPageContent` (feature/reader), qui ne connaît jamais
     * `FixedPageRenderer`/PDFium directement. `null` si aucun document
     * PDF n'est ouvert (format non PDF, ou échec d'ouverture déjà reflété
     * dans `errorMessage`).
     */
    suspend fun renderPdfPage(pageIndex: Int, targetWidthPx: Int): RenderedPage? =
        fixedPageDocument?.renderPage(pageIndex, targetWidthPx)

    /**
     * Tache 9bis.3.3 — minuteur de sommeil. Un seul job actif a la fois :
     * une nouvelle duree (ou une desactivation) annule tout minuteur en
     * cours, jamais deux qui coexistent.
     */
    private fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null) {
            _state.value = _state.value.copy(sleepTimer = null)
            return
        }
        val remainingMs = minutes * 60_000L
        _state.value = _state.value.copy(sleepTimer = SleepTimerState(remainingMs = remainingMs))
        sleepTimerJob = viewModelScope.launch {
            delay(remainingMs)
            // Même bug que Pause avant correction (voir pausePlayback) :
            // ne mettre isPlaying à false sans couper playbackJob/audio
            // laissait la phrase en cours continuer à jouer après
            // l'extinction du minuteur.
            pausePlayback()
            _state.value = _state.value.copy(sleepTimer = null)
        }
    }

    /**
     * Tâche 8.2 — écrit la surcharge de publication et recalcule
     * immédiatement `effectiveSettings` (cascade Blueprint §3.3) : la
     * surcharge prime sur les préférences globales, jamais l'inverse.
     */
    private fun setOverrides(overrides: ReadingOverrides?) {
        val publicationId = currentPublicationId ?: return
        viewModelScope.launch {
            val existing = getReadingState(publicationId)
            val baseState = existing ?: ReadingState(
                publicationId = publicationId,
                locator = Locator(resourceHref = "unknown", chapterIndex = 0, charOffset = 0),
                lastReadAt = System.currentTimeMillis(),
            )
            updateReadingState(baseState.copy(overrides = overrides, lastReadAt = System.currentTimeMillis()))
            val effectiveSettings = EffectiveReadingSettings.resolve(overrides, preferencesRepository.get())
            _state.value = _state.value.copy(
                currentOverrides = overrides,
                effectiveSettings = effectiveSettings,
                resolvedTheme = themeRepository.getById(effectiveSettings.theme) ?: ReadingTheme.DEFAULT,
            )
        }
    }

    /**
     * A.5/3d.1 — résolution unique du profil vocal actif, partagée par
     * `playCurrentSentence`, `openPublication` (état initial du panneau
     * Voix) et `setTtsSpeed`/`setActiveVoiceProfile` : avant ce lot, le
     * même repli (voix native française par défaut) était dupliqué en
     * ligne dans `playCurrentSentence` uniquement, aucune autre fonction
     * n'avait besoin de connaître le profil actif.
     */
    private suspend fun resolveVoiceProfile(prefs: UserPreferences): VoiceProfile =
        prefs.activeVoiceProfileId
            ?.let { voiceProfileRepository.getById(it) }
            ?: DEFAULT_VOICE_PROFILE

    /**
     * Ouvre une publication déjà importée : récupère son `fileUri` via
     * le repository, parse le contenu (CompositePublicationParser),
     * puis restaure la dernière position connue (K3) si elle existe.
     * Les cas d'erreur de parsing (Corrompu, DRM, format non supporté)
     * ne sont pas encore reflétés dans `ReaderUiState` — Tâche 4.8.
     */
    private fun openPublication(publicationId: String, targetLocator: Locator? = null, flashOnArrival: Boolean = false) {
        // Lot 12, tache 12.9 — une publication PDF ouverte precedemment
        // garde son FixedPageDocument vivant jusqu'ici (decision actee 14
        // du plan) ; en ouvrir une nouvelle doit d'abord fermer l'ancien,
        // jamais accumuler des handles natifs non fermes.
        fixedPageDocument?.close()
        fixedPageDocument = null
        viewModelScope.launch {
            val publication = publicationRepository.getById(publicationId) ?: run {
                Log.w("ReaderViewModel", "openPublication: publication introuvable ($publicationId)")
                _state.value = _state.value.copy(errorMessage = "Publication introuvable.")
                return@launch
            }
            when (val result = publicationParser.parse(publication.fileUri)) {
                is ParseResult.Success -> {
                    currentPublicationId = publicationId

                    // ───── Lot Sessions : démarre le tracking ─────
                    val tracker = ReadingSessionTracker(publicationId)
                    sessionTracker = tracker
                    lastFragmentSavedMs = tracker.startTimestamp
                    tracker.resume(DomainReadingMode.VISUAL)
                    startCheckpointTimer()
                    // ───── Fin Lot Sessions ─────

                    val restored = getReadingState(publicationId)
                    val effectiveSettings = EffectiveReadingSettings.resolve(
                        overrides = restored?.overrides,
                        global = preferencesRepository.get(),
                    )
                    val prefs = preferencesRepository.get()
                    val resolvedTheme = themeRepository.getById(effectiveSettings.theme) ?: ReadingTheme.DEFAULT
                    val activeVoiceProfile = resolveVoiceProfile(prefs)
                    val availableVoiceProfiles = getVoiceProfiles()
                    _state.value = ReaderUiState(
                        // 3b.3 — barre du haut (ReaderTopBar) : source
                        // unique, jamais rechargé depuis un repository
                        // dans le composable.
                        title = publication.title,
                        author = publication.authors.joinToString(", ").ifBlank { null },
                        chapters = result.documentModel.chapters,
                        tableOfContents = result.documentModel.tableOfContents,
                        currentChapterIndex = restored?.locator?.chapterIndex ?: 0,
                        effectiveSettings = effectiveSettings,
                        resolvedTheme = resolvedTheme,
                        // B.1 — restaure le mode de lecture persisté
                        readingMode = if (prefs.readingMode == "PAGED") ReadingMode.PAGED else ReadingMode.SCROLL,
                        currentOverrides = restored?.overrides,
                        activeVoiceProfile = activeVoiceProfile,
                        availableVoiceProfiles = availableVoiceProfiles,
                        lineHeightMultiplier = prefs.lineHeightMultiplier,
                        // Lot 12, tache 12.9 — jamais reporte avant ce lot.
                        publicationFormat = publication.format,
                        pageOffsetY = restored?.locator?.pageOffsetY ?: 0f,
                        // Plan v3, Palier 3.6 — ID de publication + résolveur images EPUB
                        publicationId = publicationId,
                        epubResourceResolver = if (publication.format == PublicationFormat.EPUB) {
                            this@ReaderViewModel.epubResourceResolver
                        } else null,
                    )
                    // Plan v3, Palier 3.6 — initialiser le parsing lazy EPUB
                    if (publication.format == PublicationFormat.EPUB) {
                        epubResourceResolver.open(publication.fileUri)
                        chapterParser.registerPublication(publicationId, publication.fileUri)
                    }
                    triggerPreload(_state.value.currentChapterIndex)
                    observeAnnotations(publicationId)
                    observeBookmarks(publicationId)
                    if (prefs.eyeRestReminderEnabled) scheduleEyeRestReminder(prefs.eyeRestReminderIntervalMinutes)
                    if (targetLocator != null) navigateToLocator(targetLocator, flashOnArrival)

                    // Lot 12, tache 12.9 — ouvre le document de rendu fixe
                    // pour toute la session de lecture (decision actee 14),
                    // apres avoir peuple l'etat pour que errorMessage
                    // s'affiche sur le meme ecran en cas d'echec.
                    if (publication.format == PublicationFormat.PDF) {
                        when (val openResult = fixedPageRenderer.open(publication.fileUri)) {
                            is FixedPageOpenResult.Success -> fixedPageDocument = openResult.document
                            is FixedPageOpenResult.Failed -> {
                                Log.w("ReaderViewModel", "openPublication: echec ouverture rendu PDF (${openResult.reason})")
                                _state.value = _state.value.copy(errorMessage = openResult.reason)
                            }
                        }
                    }
                }
                else -> {
                    val message = when (result) {
                        is ParseResult.DrmProtected -> result.message
                        is ParseResult.Corrupted -> result.message
                        is ParseResult.UnsupportedFormat -> "Format non supporté : ${result.format}"
                        else -> "Erreur de parsing inconnue."
                    }
                    Log.w("ReaderViewModel", "openPublication: echec de parsing ($result)")
                    _state.value = _state.value.copy(errorMessage = message)
                }
            }
        }
    }

    /**
     * Surlignage persistant (Tâche 7.1, critère de validation) : les
     * annotations déjà créées doivent réapparaître sur le texte
     * sélectionné à l'origine à la réouverture — observation continue,
     * pas un chargement ponctuel figé au moment de l'ouverture.
     */
    private fun observeAnnotations(publicationId: String) {
        viewModelScope.launch {
            annotationRepository.observeForPublication(publicationId).collect { annotations ->
                _state.value = _state.value.copy(annotations = annotations)
            }
        }
    }

    /**
     * Construit l'`Annotation` à partir de la sélection libre au mot
     * active (offsets de caractère absolus au chapitre) — jamais d'offset
     * arbitraire, jamais la phrase entière.
     *
     * **Contrat de synchronicité (Phase 4 de la refonte du cycle de vie de
     * la sélection)** : la lecture de `freeSelectionRange` et la résolution
     * des locators sont volontairement faites AVANT tout `launch`. L'UI
     * purge son état de sélection immédiatement après avoir dispatché cet
     * intent (`ReaderScreen.clearSelectionAndPopup`, pour que le lecteur
     * redevienne propre sans attendre l'écriture en base) : si la
     * résolution passait dans la coroutine, ce `ClearFreeSelection`
     * arriverait le premier et l'annotation serait silencieusement perdue.
     * Ne jamais déplacer ces lignes dans le `viewModelScope.launch`
     * ci-dessous (garde-fou : `ReaderViewModelFreeSelectionTest`).
     */
    private fun confirmAnnotation(color: AnnotationColor, content: String? = null) {
        val chapter = _state.value.currentChapter ?: return
        val publicationId = currentPublicationId ?: return
        val sentences = chapter.sentences

        val freeRange = _state.value.freeSelectionRange ?: return
        val endOffsetExclusive = freeRange.last + 1
        val (startLocator, endLocator) = annotationSelectionHandler.resolveCharRange(
            freeRange.first, endOffsetExclusive, chapter.index, chapter.href,
        ) ?: return
        val excerpt = sliceChapterText(sentences, freeRange.first, endOffsetExclusive)
            .take(Annotation.MAX_EXCERPT_LENGTH)

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            addAnnotation(
                Annotation(
                    id = UUID.randomUUID().toString(),
                    publicationId = publicationId,
                    startLocator = startLocator,
                    endLocator = endLocator,
                    color = color,
                    content = content,
                    excerpt = excerpt,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            _state.value = _state.value.copy(
                freeSelectionAnchorOffset = null, freeSelectionFocusOffset = null,
            )
        }
    }

    /** Tâche 7.2 — même principe que [observeAnnotations] : observation continue, pas un chargement figé. */
    private fun observeBookmarks(publicationId: String) {
        viewModelScope.launch {
            bookmarkRepository.observeForPublication(publicationId).collect { bookmarks ->
                _state.value = _state.value.copy(bookmarks = bookmarks)
            }
        }
    }

    /**
     * Tâche 3c.3 — toggle « Marquer cette page » : retire le signet déjà
     * présent à la position courante s'il existe (jamais de doublon,
     * cible confirmée dans `UX_FLOW_DESIGN.md`), sinon en crée un. Même
     * conversion `Sentence.startLocator` que [persistPosition]/
     * [playCurrentSentence] — une seule source pour « la position
     * courante », jamais un second calcul.
     */
    private fun toggleBookmarkAtCurrentPosition() {
        val publicationId = currentPublicationId ?: return

        // Lot 12, tache 12.9, decision actee 17 — meme Locator, memes Use
        // Cases, granularite page (pas de Sentence a chercher).
        if (_state.value.publicationFormat == PublicationFormat.PDF) {
            val chapterIndex = _state.value.currentChapterIndex
            val existing = _state.value.bookmarks.firstOrNull { it.locator.chapterIndex == chapterIndex }
            viewModelScope.launch {
                if (existing != null) {
                    deleteBookmark(existing.id)
                } else {
                    createBookmark(
                        Bookmark(
                            id = UUID.randomUUID().toString(),
                            publicationId = publicationId,
                            locator = Locator(
                                resourceHref = "page-$chapterIndex",
                                chapterIndex = chapterIndex,
                                charOffset = 0,
                                pageOffsetY = _state.value.pageOffsetY,
                            ),
                            excerpt = "Page ${chapterIndex + 1}",
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            return
        }

        val chapter = _state.value.currentChapter ?: return
        val sentence = chapter.sentences.getOrNull(_state.value.currentSentenceIndex) ?: return
        val existing = _state.value.bookmarks.firstOrNull { bookmark ->
            bookmark.locator.chapterIndex == chapter.index &&
                bookmark.locator.charOffset in sentence.startOffset until sentence.endOffset
        }

        viewModelScope.launch {
            if (existing != null) {
                deleteBookmark(existing.id)
            } else {
                createBookmark(
                    Bookmark(
                        id = UUID.randomUUID().toString(),
                        publicationId = publicationId,
                        locator = sentence.startLocator(chapterIndex = chapter.index, resourceHref = chapter.href),
                        excerpt = sentence.text.take(Bookmark.MAX_EXCERPT_LENGTH),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    /**
     * Navigue vers un `Locator` de signet (Tâche 7.2) — change de chapitre
     * si besoin puis positionne sur la `Sentence` la plus proche de
     * `charOffset`, contrairement à `navigateToChapter` seul qui repositionne
     * toujours à l'index 0.
     */
    private fun navigateToLocator(locator: Locator, flashOnArrival: Boolean = false) {
        val chapters = _state.value.chapters
        if (locator.chapterIndex !in chapters.indices) return
        val sentences = chapters[locator.chapterIndex].sentences
        val sentenceIndex = sentences.indexOfFirst { locator.charOffset in it.startOffset..it.endOffset }.coerceAtLeast(0)

        // Bug réel trouvé à l'audit, même famille que Pause avant
        // correction : sans pausePlayback() ici, un saut vers un signet
        // ou un résultat de recherche pendant une lecture TTS active
        // laissait l'ancien AudioTrack jouer et la boucle d'auto-avance
        // de playCurrentSentence() continuer sur les indices de l'ancien
        // chapitre — désynchronisation audio/affichage, violation directe
        // de K3 (chemins manuel et TTS jamais simultanés). Reprend la
        // lecture sur la nouvelle position si elle était déjà active,
        // même principe que skipSentence().
        val wasPlaying = _state.value.isPlaying
        pausePlayback()

        _state.value = _state.value.copy(
            currentChapterIndex = locator.chapterIndex, currentSentenceIndex = sentenceIndex,
            highlightedWordRange = null, isTocVisible = false, isBookmarkListVisible = false,
        )
        persistPosition(chapterIndex = locator.chapterIndex, sentenceIndex = sentenceIndex)
        triggerPreload(locator.chapterIndex)
        if (wasPlaying) playCurrentSentence()
        if (flashOnArrival) armPendingHighlight(PendingHighlightTarget(locator.chapterIndex, sentenceIndex))
    }

    /**
     * Lot 4, tâche 4.7 — arme la cible en attente et sa sortie de secours :
     * si `ChapterLayoutCompleted` n'arrive jamais (chapitre en erreur,
     * livre refermé avant la fin de la mesure), la cible est abandonnée
     * plutôt que de rester en attente indéfiniment.
     */
    private fun armPendingHighlight(target: PendingHighlightTarget) {
        pendingHighlightTimeoutJob?.cancel()
        _state.value = _state.value.copy(pendingHighlightTarget = target)
        pendingHighlightTimeoutJob = viewModelScope.launch {
            delay(PENDING_HIGHLIGHT_TIMEOUT_MS)
            if (_state.value.pendingHighlightTarget == target) {
                _state.value = _state.value.copy(pendingHighlightTarget = null)
            }
        }
    }

    /**
     * Lot 4, tâche 4.7 — déclenché par `ReaderScreen` une fois la mise en
     * page du chapitre confirmée complète. Consommation unique : la cible
     * est effacée immédiatement, une mesure suivante (changement de taille
     * de police, par exemple) ne peut donc pas rejouer le flash.
     */
    private fun onChapterLayoutCompleted(chapterIndex: Int) {
        val target = _state.value.pendingHighlightTarget ?: return
        if (target.chapterIndex != chapterIndex) return
        pendingHighlightTimeoutJob?.cancel()
        val sentence = _state.value.chapters.getOrNull(chapterIndex)
            ?.paragraphs?.flatMap { it.sentences }?.getOrNull(target.sentenceIndex)
        _state.value = _state.value.copy(
            pendingHighlightTarget = null,
            highlightedWordRange = sentence?.let { 0 until it.text.length },
        )
        flashClearJob?.cancel()
        flashClearJob = viewModelScope.launch {
            delay(FLASH_HIGHLIGHT_DURATION_MS)
            if (_state.value.currentSentenceIndex == target.sentenceIndex && !_state.value.isPlaying) {
                _state.value = _state.value.copy(highlightedWordRange = null)
            }
        }
    }

    private fun navigateToChapter(targetIndex: Int) {
        val chapters = _state.value.chapters
        if (targetIndex !in chapters.indices) return // pas de navigation hors bornes silencieuse

        // Même correction que navigateToLocator (bug réel trouvé à
        // l'audit, K3) : couvre à la fois la navigation manuelle
        // (chevrons, TOC) ET l'auto-avance interne de playCurrentSentence
        // en fin de chapitre — dans ce dernier cas, pausePlayback()
        // annule sa propre coroutine (playbackJob pointe déjà vers elle),
        // mais playCurrentSentence() ci-dessous en relance aussitôt une
        // nouvelle pour le chapitre suivant ; la coroutine d'origine se
        // termine alors silencieusement à son prochain point de
        // suspension (delay), sans rejouer la phrase en double.
        val wasPlaying = _state.value.isPlaying
        pausePlayback()

        _state.value = _state.value.copy(
            currentChapterIndex = targetIndex, currentSentenceIndex = 0,
            highlightedWordRange = null, isTocVisible = false,
        )
        persistPosition(chapterIndex = targetIndex, sentenceIndex = 0)
        // Plan v3, Palier 3.6 — charger le contenu Rich si chapitre vide
        loadChapterContentIfNeeded(targetIndex)
        triggerPreload(targetIndex)
        if (wasPlaying) playCurrentSentence()
    }

    /**
     * Plan v3, Palier 3.6 — charge le contenu d'un chapitre Rich vide
     * via [ChapterParser.parseChapter]. Les chapitres Legacy (PDF/TXT)
     * ou déjà chargés sont ignorés.
     */
    private fun loadChapterContentIfNeeded(chapterIndex: Int) {
        val chapter = _state.value.chapters.getOrNull(chapterIndex) ?: return
        if (chapter.content !is ChapterContent.Rich) return
        val rich = chapter.content as ChapterContent.Rich
        if (rich.blocks.isNotEmpty()) return // déjà chargé

        val publicationId = currentPublicationId ?: return
        viewModelScope.launch {
            val richChapter = chapterParser.parseChapter(publicationId, chapter.href)
            val chapters = _state.value.chapters.toMutableList()
            chapters[chapterIndex] = richChapter
            _state.value = _state.value.copy(chapters = chapters)
        }
    }

    private fun triggerPreload(currentIndex: Int) {
        val nextChapter = _state.value.chapters.getOrNull(currentIndex + 1)
        chapterPreloader.preload(nextChapter) { /* chapitre pret, no-op pour l'instant */ }
    }

    /**
     * Chemin manuel K3 (Blueprint §7.7) — distinct du chemin TTS
     * (playCurrentSentence). Les deux ne s'exécutent jamais simultanément :
     * la navigation manuelle interrompt implicitement toute lecture en
     * cours (isPlaying repasse a false via l'etat recompose).
     */
    private fun persistPosition(chapterIndex: Int, sentenceIndex: Int) {
        viewModelScope.launch {
            val publicationId = currentPublicationId ?: return@launch
            // Lot 12, tache 12.9 — branche PDF : page = chapitre, jamais
            // de Sentence a chercher (une page scannee n'en a aucune, ce
            // qui faisait echouer silencieusement ce chemin avant cette
            // branche — bug trouve en cablant cette tache). `sentenceIndex`
            // est ignore pour ce format, `pageOffsetY` deja dans l'etat.
            val locator = if (_state.value.publicationFormat == PublicationFormat.PDF) {
                Locator(
                    resourceHref = "page-$chapterIndex",
                    chapterIndex = chapterIndex,
                    charOffset = 0,
                    pageOffsetY = _state.value.pageOffsetY,
                )
            } else {
                val chapter = _state.value.chapters.getOrNull(chapterIndex) ?: return@launch
                val sentence = chapter.sentences.getOrNull(sentenceIndex) ?: return@launch
                sentence.startLocator(chapterIndex = chapterIndex, resourceHref = chapter.href)
            }
            updateReadingState(
                ReadingState(
                    publicationId = publicationId,
                    locator = locator,
                    lastReadAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * A.1 — Lecture TTS continue phrase à phrase. Après avoir joué la
     * phrase courante, avance automatiquement à la phrase suivante dans
     * le même chapitre, puis au chapitre suivant si le chapitre en cours
     * est terminé. La récursion est trampolinée par coroutine (pas de
     * stack overflow).
     *
     * L'arrêt se fait via [ReaderIntent.Pause] → [pausePlayback], qui
     * annule ce job et coupe l'audio — la boucle vérifie aussi `isPlaying`
     * avant chaque avancement pour les cas où le job irait jusqu'au bout
     * de la phrase en cours avant que l'annulation ne soit observée.
     */
    private fun playCurrentSentence() {
        // Lot 12, tache 12.10 — TTS hors perimetre pour un PDF (decision
        // actee 16). Le bouton declencheur est deja masque (UnifiedControlPanel,
        // ReaderTtsPanel inatteignable) ; cette garde couvre un
        // declencheur externe eventuel (MediaSession/ecran verrouille),
        // jamais audite pour ce format.
        if (_state.value.publicationFormat == PublicationFormat.PDF) return
        playbackJob = viewModelScope.launch {
            val chapter = _state.value.currentChapter ?: return@launch
            val sentences = chapter.sentences
            val index = _state.value.currentSentenceIndex
            val publicationId = currentPublicationId ?: return@launch

            // ───── Lot Sessions : bascule en mode TTS ─────
            sessionTracker?.switchMode(DomainReadingMode.AUDIO)
            // ───── Fin Lot Sessions ─────

            if (index >= sentences.size) {
                // Fin de chapitre → auto-avance chapitre suivant si possible.
                // navigateToChapter() (appelée par NextChapter) relance elle-même
                // playCurrentSentence() sur le nouveau chapitre puisque isPlaying
                // est encore vrai ici — pas besoin de le refaire depuis ce
                // point, qui de toute façon ne serait jamais atteint : cette
                // coroutine est annulée par le pausePlayback() interne à
                // navigateToChapter avant d'y revenir.
                if (_state.value.hasNextChapter) {
                    onIntent(ReaderIntent.NextChapter)
                } else {
                    _state.value = _state.value.copy(isPlaying = false, isAudioActive = false)
                }
                return@launch
            }

            // 3e.3 — isAudioActive reste faux pendant la synthèse
            // (sentenceAudioBuffer.get, potentiellement lente si le
            // segment n'est pas déjà préchargé) : isPlaying seul ne suffit
            // pas à distinguer « TTS engagé » de « audio effectivement en
            // train de sortir », voir ReaderUiState.isAudioActive.
            _state.value = _state.value.copy(isPlaying = true, isAudioActive = false)

            val sentence = sentences[index]
            // A.5 — résout le profil vocal actif depuis les préférences utilisateur
            val prefs = preferencesRepository.get()
            val voiceProfile = resolveVoiceProfile(prefs)
            val segment = sentenceAudioBuffer.get(sentence, voiceProfile)
            audioSegmentPlayer.play(segment)
            _state.value = _state.value.copy(isAudioActive = true)

            // Lot 10 — retour Issa (vérification device) : proposition
            // proactive au premier usage réel du TTS, plutôt que de
            // laisser le repli Android natif (ADR-021, FallbackTtsEngine)
            // silencieux sans jamais suggérer la voix neuronale.
            // ttsEngine.id reflète le moteur RÉELLEMENT actif après ce
            // synthesize() (jamais figé, voir FallbackTtsEngine), donc
            // couvre aussi bien "réglé sur Android natif" que "Sherpa-ONNX
            // a échoué et le repli vient de se produire".
            if (ttsEngine.id == TtsEngineId.ANDROID_NATIVE && !prefs.hasPromptedVoiceDownload) {
                _state.value = _state.value.copy(showVoiceDownloadPrompt = true)
                preferencesRepository.update(prefs.copy(hasPromptedVoiceDownload = true))
            }

            // Précharge la phrase suivante pendant que celle-ci se joue
            sentences.getOrNull(index + 1)?.let {
                sentenceAudioBuffer.preloadNext(it, voiceProfile)
            }

            segment.wordTimestamps.forEach { wt ->
                _state.value = _state.value.copy(
                    highlightedWordRange = wt.charOffset until (wt.charOffset + wt.word.length),
                )
                delay((wt.endMs - wt.startMs).coerceAtLeast(0L))
            }

            _state.value = _state.value.copy(highlightedWordRange = null, isAudioActive = false)

            updateReadingState(
                ReadingState(
                    publicationId = publicationId,
                    locator = sentence.startLocator(chapterIndex = chapter.index, resourceHref = chapter.href),
                    lastReadAt = System.currentTimeMillis(),
                ),
            )

            // Avance à la phrase suivante UNIQUEMENT si toujours en lecture
            if (_state.value.isPlaying) {
                _state.value = _state.value.copy(currentSentenceIndex = index + 1)
                playCurrentSentence()
            }
        }
    }

    /**
     * Interrompt réellement la lecture en cours : annule la coroutine de
     * [playCurrentSentence] (sinon la boucle de surlignage mot-à-mot
     * continue d'avancer silencieusement) et coupe l'`AudioTrack` sous-
     * jacent (sinon la phrase en cours continue de se faire entendre
     * jusqu'à sa fin après un appui sur Pause).
     */
    private fun pausePlayback() {
        playbackJob?.cancel()
        audioSegmentPlayer.stop()
        _state.value = _state.value.copy(isPlaying = false, isAudioActive = false, highlightedWordRange = null)

        // ───── Lot Sessions : retour en mode visuel ─────
        sessionTracker?.switchMode(DomainReadingMode.VISUAL)
        // ───── Fin Lot Sessions ─────
    }

    /**
     * Panneau TTS (Tâche B.3) — recule/avance d'une phrase dans le
     * chapitre courant. Reprend immédiatement la lecture sur la nouvelle
     * phrase si elle était déjà en cours ; sinon se contente de déplacer
     * la position (mêmes règles K3 que la navigation manuelle).
     */
    private fun skipSentence(delta: Int) {
        val chapter = _state.value.currentChapter ?: return
        val sentences = chapter.sentences
        if (sentences.isEmpty()) return
        val wasPlaying = _state.value.isPlaying
        pausePlayback()
        val newIndex = (_state.value.currentSentenceIndex + delta).coerceIn(0, sentences.lastIndex)
        _state.value = _state.value.copy(currentSentenceIndex = newIndex)
        persistPosition(chapterIndex = chapter.index, sentenceIndex = newIndex)
        if (wasPlaying) playCurrentSentence()
    }

    /**
     * 3d.1 — écrit la vitesse sur le profil vocal actif (jamais sur
     * `UserPreferences`, voir doc du lot 3d tâche 3d.1). Si aucun profil
     * n'était actif (repli natif par défaut, jamais persisté), ce premier
     * réglage le persiste et l'active — sinon la vitesse choisie serait
     * perdue à la prochaine résolution (`resolveVoiceProfile` retomberait
     * sur le même repli non modifié).
     */
    private fun setTtsSpeed(speed: Float) {
        viewModelScope.launch {
            val prefs = preferencesRepository.get()
            val updated = resolveVoiceProfile(prefs).copy(speed = speed)
            voiceProfileRepository.save(updated)
            if (prefs.activeVoiceProfileId != updated.id) {
                preferencesRepository.update(prefs.copy(activeVoiceProfileId = updated.id))
            }
            _state.value = _state.value.copy(activeVoiceProfile = updated)
        }
    }

    /** 3d.1 — change le profil vocal actif (préférence globale, panneau Voix). */
    private fun setActiveVoiceProfile(profileId: String) {
        viewModelScope.launch {
            val prefs = preferencesRepository.get()
            preferencesRepository.update(prefs.copy(activeVoiceProfileId = profileId))
            _state.value = _state.value.copy(activeVoiceProfile = voiceProfileRepository.getById(profileId))
        }
    }

    /** 3d.2 — réglage global d'interligne, voir `ReaderUiState.lineHeightMultiplier`. */
    private fun setLineHeight(multiplier: Float) {
        viewModelScope.launch {
            val current = preferencesRepository.get()
            preferencesRepository.update(current.copy(lineHeightMultiplier = multiplier))
        }
    }

    /** 3d.3 — réglage global de luminosité, voir `ReaderUiState.readerBrightness`. */
    private fun setReaderBrightness(value: Float?) {
        viewModelScope.launch {
            val current = preferencesRepository.get()
            preferencesRepository.update(current.copy(readerBrightness = value))
        }
    }

    /**
     * 3d.5 — programme l'échéance du rappel de repos oculaire. Un seul job
     * actif à la fois (même principe que `setSleepTimer`) : reprogrammer
     * annule tout délai en cours plutôt que d'en laisser deux coexister.
     */
    private fun scheduleEyeRestReminder(afterMinutes: Int) {
        eyeRestReminderJob?.cancel()
        eyeRestReminderJob = viewModelScope.launch {
            delay(afterMinutes * 60_000L)
            triggerEyeRestReminder()
        }
    }

    /**
     * 3d.5 — à l'échéance : coupe le TTS s'il est actif (jamais
     * silencieusement — le popup visible EST l'avertissement, voir doc du
     * lot 3d tâche 3d.5 et consignation 3d.7 sur le comportement audio
     * retenu), affiche le popup, démarre le compte à rebours de 60s.
     */
    private fun triggerEyeRestReminder() {
        wasPlayingBeforeEyeRest = _state.value.isPlaying
        if (wasPlayingBeforeEyeRest) pausePlayback()
        _state.value = _state.value.copy(
            isEyeRestReminderVisible = true,
            eyeRestReminderCountdownS = EYE_REST_REMINDER_COUNTDOWN_S,
        )
        eyeRestCountdownJob?.cancel()
        eyeRestCountdownJob = viewModelScope.launch {
            repeat(EYE_REST_REMINDER_COUNTDOWN_S) {
                delay(1_000L)
                _state.value = _state.value.copy(
                    eyeRestReminderCountdownS = (_state.value.eyeRestReminderCountdownS - 1).coerceAtLeast(0),
                )
            }
            // Countdown écoulé sans action explicite : équivalent à
            // "Reprendre" — l'audio ne doit jamais rester coupé
            // indéfiniment sans action de l'utilisateur.
            resumeFromEyeRestReminder()
        }
    }

    /** 3d.5 — « Reprendre » (explicite ou countdown écoulé), voir `ReaderIntent.ResumeFromEyeRestReminder`. */
    private fun resumeFromEyeRestReminder() {
        eyeRestCountdownJob?.cancel()
        _state.value = _state.value.copy(isEyeRestReminderVisible = false)
        if (wasPlayingBeforeEyeRest) playCurrentSentence()
        viewModelScope.launch {
            val prefs = preferencesRepository.get()
            if (prefs.eyeRestReminderEnabled) scheduleEyeRestReminder(prefs.eyeRestReminderIntervalMinutes)
        }
    }

    /** 3d.5 — « Reporter », voir `ReaderIntent.SnoozeEyeRestReminder`. */
    private fun snoozeEyeRestReminder() {
        eyeRestCountdownJob?.cancel()
        _state.value = _state.value.copy(isEyeRestReminderVisible = false)
        scheduleEyeRestReminder(EYE_REST_REMINDER_SNOOZE_MINUTES)
    }

    /** 3d.5 — réglage global, voir `ReaderUiState.eyeRestReminderEnabled`. */
    private fun setEyeRestReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val prefs = preferencesRepository.get()
            preferencesRepository.update(prefs.copy(eyeRestReminderEnabled = enabled))
            if (enabled) {
                scheduleEyeRestReminder(prefs.eyeRestReminderIntervalMinutes)
            } else {
                eyeRestReminderJob?.cancel()
            }
        }
    }

    /** 3d.5 — réglage global, voir `ReaderUiState.eyeRestReminderIntervalMinutes`. */
    private fun setEyeRestReminderInterval(minutes: Int) {
        viewModelScope.launch {
            val prefs = preferencesRepository.get()
            preferencesRepository.update(prefs.copy(eyeRestReminderIntervalMinutes = minutes))
            if (prefs.eyeRestReminderEnabled) scheduleEyeRestReminder(minutes)
        }
    }

    /**
     * A.2 — Nettoyage des ressources audio et du minuteur de sommeil
     * quand le ViewModel est détruit. Évite qu'un segment audio continue
     * de jouer après la destruction de l'écran.
     */
    override fun onCleared() {
        // ───── Lot Sessions : sauvegarde finale + arrêt timer ─────
        saveCurrentFragment()
        checkpointJob?.cancel()
        // ───── Fin Lot Sessions ─────

        super.onCleared()
        audioSegmentPlayer.stop()
        sleepTimerJob?.cancel()
        scrollPersistJob?.cancel()
        eyeRestReminderJob?.cancel()
        eyeRestCountdownJob?.cancel()
        // Lot 12, tache 12.9 — libere les ressources natives PDFium du
        // renderer, jamais laisse au ramasse-miettes (decision actee 14).
        pageOffsetPersistJob?.cancel()
        fixedPageDocument?.close()
        fixedPageDocument = null
    }

    // ═══════════════════════════════════════════════
    // Lot Sessions — checkpointing et persistance
    // ═══════════════════════════════════════════════

    /**
     * Appelé par [ReaderScreen] sur ON_STOP. Sauve un fragment
     * et met le tracker en pause — le temps en background ne doit
     * pas être comptabilisé comme du temps de lecture.
     */
    fun onAppBackground() {
        saveCurrentFragment()
        sessionTracker?.pause()
    }

    /**
     * Appelé par [ReaderScreen] sur ON_START. Reprend le tracker
     * dans le mode dicté par l'état TTS courant.
     */
    fun onAppForeground() {
        val tracker = sessionTracker ?: return
        val mode = if (_state.value.isPlaying) DomainReadingMode.AUDIO else DomainReadingMode.VISUAL
        tracker.resume(mode)
    }

    /**
     * Annule le timer de checkpoint depuis un test JVM. `ViewModel.clear()`
     * et `onCleared()` sont respectivement `internal` et `protected` dans
     * androidx.lifecycle et donc inaccessibles depuis les classes de test
     * (ni sous-classes, ni même module externe) — ce timer étant
     * auto-récurrent (`while(true) { delay(...) }`), l'oublier annuler
     * ferait boucler indéfiniment le drain implicite de fin de `runTest`.
     */
    @VisibleForTesting
    internal fun cancelCheckpointTimerForTest() {
        checkpointJob?.cancel()
    }

    /** Timer de checkpoint : sauve un fragment toutes les 5 minutes. */
    private fun startCheckpointTimer() {
        checkpointJob?.cancel()
        checkpointJob = viewModelScope.launch {
            while (true) {
                delay(CHECKPOINT_INTERVAL_MS)
                val t = sessionTracker ?: continue
                if (t.isPaused) continue
                val (v, tts) = t.snapshot()
                if (v + tts < 5_000L) continue
                persistFragment(v, tts)
                t.reset()
            }
        }
    }

    /** Flush + save d'un fragment sans pauser le tracker. */
    private fun saveCurrentFragment() {
        val t = sessionTracker ?: return
        val (v, tts) = t.snapshot()
        if (v + tts < 5_000L) return
        persistFragment(v, tts)
        t.reset()
    }

    /**
     * Insère un fragment de session en base (Dispatchers.IO).
     * Chaque fragment est non-chevauchant : [lastFragmentSavedMs]
     * avance à chaque sauvegarde.
     */
    private fun persistFragment(visualMs: Long, ttsMs: Long) {
        val fragmentStart = lastFragmentSavedMs
        lastFragmentSavedMs = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            readingSessionRepository.insert(
                ReadingSession(
                    id = java.util.UUID.randomUUID().toString(),
                    publicationId = sessionTracker!!.publicationId,
                    startedAt = fragmentStart,
                    endedAt = lastFragmentSavedMs,
                    mode = if (ttsMs >= visualMs) DomainReadingMode.AUDIO else DomainReadingMode.VISUAL,
                    visualDurationMs = visualMs,
                    ttsDurationMs = ttsMs,
                )
            )
        }
    }

    companion object {
        /** Intervalle de checkpoint : 5 minutes. */
        private const val CHECKPOINT_INTERVAL_MS = 5 * 60 * 1000L
    }
}

/** Tâche 3c.1 — au changement de phrase visible, pas à chaque pixel défilé. */
private const val SCROLL_PERSIST_DEBOUNCE_MS = 400L

/** Lot 4, tâche 4.7 — sortie de secours si la mise en page n'aboutit jamais. */
private const val PENDING_HIGHLIGHT_TIMEOUT_MS = 8_000L

/** Lot 4, tâche 4.7 — durée d'affichage du flash avant effacement automatique. */
private const val FLASH_HIGHLIGHT_DURATION_MS = 2_500L

/** 3d.5 — snooze court du popup de repos oculaire, distinct de l'intervalle configuré. */
private const val EYE_REST_REMINDER_SNOOZE_MINUTES = 10

/** A.5 — repli quand `UserPreferences.activeVoiceProfileId` est `null`. */
private val DEFAULT_VOICE_PROFILE = VoiceProfile(
    id = "vp-native-fr", engine = TtsEngineId.ANDROID_NATIVE,
    voice = "fr-fr-default", language = "fr-FR",
)
