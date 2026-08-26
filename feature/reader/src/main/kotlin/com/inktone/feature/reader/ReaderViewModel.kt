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
import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.EffectiveReadingSettings
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingMode as DomainReadingMode
import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingSession
import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.cleanedAuthorsForDisplay
import com.inktone.domain.model.cleanedForDisplay
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
import com.inktone.domain.service.RenderedPageCache
import com.inktone.domain.service.TrackerSnapshot
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.service.WordTimestamp
import com.inktone.domain.usecase.AddAnnotationUseCase
import com.inktone.domain.usecase.CreateBookmarkUseCase
import com.inktone.domain.usecase.DeleteAnnotationUseCase
import com.inktone.domain.usecase.DeleteBookmarkUseCase
import com.inktone.domain.usecase.UpdateAnnotationUseCase
import com.inktone.domain.usecase.UpdateBookmarkNoteUseCase
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.GetVoiceProfilesUseCase
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import com.inktone.domain.valueobject.Locator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * MVI complet du Reader (Tâche 4.5) — remplace le squelette à une seule
 * phrase de la Phase 3 par la navigation par chapitre, la TOC et la
 * reprise de position réelle. L'audio est joué via [PlaybackOrchestrator]
 * (pipeline gapless, Lot 15) ; `isPlaying`/`isAudioActive`/
 * `currentSentenceIndex` dérivent de l'état de l'ordonnanceur.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val ttsEngine: TtsEngine, // injecte AndroidNativeTtsEngine (Palier 1) via Hilt (infrastructure/tts/di/TtsModule)
    private val playbackOrchestrator: PlaybackOrchestrator,
    private val publicationParser: PublicationParser, // CompositePublicationParser via Hilt (infrastructure/parser/di/ParserModule)
    private val updateReadingState: UpdateReadingStateUseCase,
    private val getReadingState: GetReadingStateUseCase,
    private val publicationRepository: PublicationRepository,
    private val preferencesRepository: PreferencesRepository,
    private val voiceProfileRepository: VoiceProfileRepository,
    private val getVoiceProfiles: GetVoiceProfilesUseCase,
    private val annotationRepository: AnnotationRepository,
    private val addAnnotation: AddAnnotationUseCase,
    // Lot 22, tâche 11 — édition de note et suppression depuis
    // `BookmarkPanel` ; même patron que updateBookmarkNote (valeur par
    // défaut pour ne pas casser les tests qui construisent ce ViewModel
    // sans passer ces paramètres).
    private val updateAnnotation: UpdateAnnotationUseCase = UpdateAnnotationUseCase(annotationRepository),
    private val deleteAnnotation: DeleteAnnotationUseCase = DeleteAnnotationUseCase(annotationRepository),
    private val bookmarkRepository: BookmarkRepository,
    private val createBookmark: CreateBookmarkUseCase,
    private val deleteBookmark: DeleteBookmarkUseCase,
    // Correctif Lot 21 — même patron que createBookmark/deleteBookmark ;
    // valeur par défaut pour ne pas casser les tests qui construisent ce
    // ViewModel sans passer ce paramètre.
    private val updateBookmarkNote: UpdateBookmarkNoteUseCase = UpdateBookmarkNoteUseCase(bookmarkRepository),
    // ───── Lot Sessions ─────
    private val readingSessionRepository: ReadingSessionRepository,
    // Lot 9 — résolution id → ReadingTheme complet (couleurs + police).
    private val themeRepository: ThemeRepository,
    // Lot 12, Palier 2 — rendu bitmap PDF (PdfPageRendererImpl via Hilt,
    // infrastructure/parser/di/ParserModule). Jamais le binding PDFium
    // directement (règle de dépendance, Blueprint §4.7).
    private val fixedPageRenderer: FixedPageRenderer,
    // Lot 22, Palier C, tâche 9 — cache disque des pages PDF déjà rendues,
    // purgé avec la publication (DeletePublicationUseCase).
    private val renderedPageCache: RenderedPageCache,
    // Plan v3, Palier 3.6 — parsing lazy EPUB + résolveur d'images
    private val chapterParser: ChapterParser,
    private val epubResourceResolver: EpubResourceResolver,
    // P2-b — relais du suivi statistique quand cet écran meurt pendant une
    // narration : le tracker lui est cédé, jamais partagé.
    private val narrationSessionContinuation: NarrationSessionContinuation,
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
                    readerMarginStep = preferences.readerMarginStep,
                    isTextJustified = preferences.textJustified,
                    keepScreenOn = preferences.keepScreenOn,
                    readerBrightness = preferences.readerBrightness,
                    eyeRestReminderEnabled = preferences.eyeRestReminderEnabled,
                    eyeRestReminderIntervalMinutes = preferences.eyeRestReminderIntervalMinutes,
                    reduceMotion = preferences.reduceMotion,
                    autoScrollSpeed = preferences.autoScrollSpeed,
                    recentAnnotationColors = preferences.recentAnnotationColors,
                )
            }
        }

        // Lot 15 (Tâche 4.1) — `isAudioActive` dérive de l'état de
        // l'ordonnanceur ; la fin naturelle d'un chapitre (Idle alors que la
        // lecture était engagée) déclenche l'auto-avance.
        viewModelScope.launch {
            playbackOrchestrator.state.collect { status ->
                when (status) {
                    PlaybackOrchestrator.PlaybackStatus.Idle ->
                        // P1 — plus d'auto-avance déduite ici : `Idle` signifie
                        // seulement « plus rien ne joue », qu'il s'agisse d'une
                        // fin de chapitre, d'un arrêt volontaire ou d'une pause
                        // demandée pendant la synthèse. La fin de chapitre a son
                        // signal propre (`chapterCompleted`, collecté plus bas).
                        _state.value = _state.value.copy(
                            isPlaying = false, isAudioActive = false, highlightedWordRange = null,
                        )
                    PlaybackOrchestrator.PlaybackStatus.Buffering ->
                        _state.value = _state.value.copy(isAudioActive = false)
                    PlaybackOrchestrator.PlaybackStatus.Playing -> {
                        // P1 — isPlaying = true ici (et pas seulement dans
                        // playCurrentSentence) : une reprise déclenchée par la
                        // notification (PlaybackSession.resume) doit se
                        // refléter dans l'état du Lecteur.
                        _state.value = _state.value.copy(isAudioActive = true, isPlaying = true)
                        checkVoiceDownloadPrompt()
                    }
                    PlaybackOrchestrator.PlaybackStatus.Paused ->
                        // P1 — vraie pause (PlaybackSession.pause) : le Lecteur
                        // doit refléter isPlaying = false sans auto-avancer
                        // (seul Idle déclenche la fin de chapitre).
                        _state.value = _state.value.copy(isAudioActive = false, isPlaying = false)
                    is PlaybackOrchestrator.PlaybackStatus.Error ->
                        _state.value = _state.value.copy(
                            isPlaying = false, isAudioActive = false, highlightedWordRange = null,
                            errorMessage = status.message,
                        )
                }
            }
        }

        // P2-b — le minuteur de sommeil est porté par la session ; l'écran ne
        // fait que l'afficher (SleepTimerPanel, ligne de statut).
        viewModelScope.launch {
            playbackOrchestrator.sleepTimer.collect { timer ->
                _state.value = _state.value.copy(sleepTimer = timer)
            }
        }

        // P2-b — l'écran SUIT le chapitre narré, il ne le pilote plus.
        // L'auto-avance vit désormais dans l'ordonnanceur (seul capable de
        // continuer quand cet écran est détruit) ; ici on se contente de
        // recaler l'affichage sur le chapitre qu'il a décidé de jouer.
        viewModelScope.launch {
            playbackOrchestrator.currentChapterIndex.collect { chapterIndex ->
                if (!_state.value.isPlaying) return@collect
                if (chapterIndex == _state.value.currentChapterIndex) return@collect
                syncDisplayToNarratedChapter(chapterIndex)
            }
        }

        // Lot 16 (Tâche 2.2) — le surlignage mot suit la position réelle de
        // l'ordonnanceur (getTimestamp, repli delay() géré ci-dessous).
        viewModelScope.launch {
            playbackOrchestrator.currentWordRange.collect { range ->
                if (_state.value.isPlaying) {
                    _state.value = _state.value.copy(highlightedWordRange = range)
                }
            }
        }

        // Lot 16 (Tâche 2.2) — repli delay() quand la position est invalide :
        // l'ancien startWordHighlight devient le filet de sécurité, déclenché
        // uniquement si l'ordonnanceur n'a pas de position exploitable.
        viewModelScope.launch {
            combine(
                playbackOrchestrator.currentWordTimestamps,
                playbackOrchestrator.positionValid,
            ) { timestamps, valid -> timestamps to valid }
                .collect { (timestamps, valid) ->
                    if (!_state.value.isPlaying) return@collect
                    if (!valid) {
                        startWordHighlight(timestamps)
                    } else {
                        highlightJob?.cancel()
                    }
                }
        }

        // Lot 15 (Tâche 4.1) — `currentSentenceIndex` suit l'ordonnanceur
        // pendant la lecture ; hors lecture, la navigation manuelle reste
        // propriétaire de cet index (K3, chemins jamais simultanés).
        viewModelScope.launch {
            playbackOrchestrator.currentSentenceIndex.collect { index ->
                if (_state.value.isPlaying) {
                    _state.value = _state.value.copy(currentSentenceIndex = index)
                }
            }
        }

        // Chantier statistiques V1 — les mots prononcés alimentent le tracker
        // tant que CET écran en est propriétaire. Dès qu'il le cède au relais
        // (`continueTracking`), `sessionTracker` est nul et ce collecteur ne
        // crédite plus rien : c'est le relais qui prend la suite, jamais les
        // deux ensemble.
        viewModelScope.launch {
            playbackOrchestrator.narratedSentenceWords.collect { words ->
                sessionTracker?.addProgress(words)
            }
        }
    }

    // C.5 — exposé pour clé sharedElement dans ReaderScreen
    internal var currentPublicationId: String? = null
    /** Plan v4 — scope dédié aux préchargements, annulé indépendamment du chargement courant. */
    private var preloadScope: CoroutineScope? = null
    private val annotationSelectionHandler = AnnotationSelectionHandler()

    // ───── Lot Sessions ─────
    private var sessionTracker: ReadingSessionTracker? = null

    /** Chapitre auquel se rapporte [visualProgressHighWaterMark]. */
    private var visualProgressChapterIndex: Int = -1

    /** Phrase la plus avancée déjà créditée au défilement dans ce chapitre. */
    private var visualProgressHighWaterMark: Int = -1
    private var checkpointJob: Job? = null
    private var lastFragmentSavedMs: Long = 0L
    // ───── Fin Lot Sessions ─────

    // 3d.5 — rappel de repos oculaire : eyeRestReminderJob porte le délai
    // jusqu'à l'échéance (relancé à chaque reprise, jamais deux en
    // parallèle) ; eyeRestCountdownJob porte le
    // compte à rebours de 60s du popup une fois affiché ;
    // wasPlayingBeforeEyeRest mémorise s'il faut reprendre le TTS.
    private var eyeRestReminderJob: Job? = null
    private var eyeRestCountdownJob: Job? = null
    private var wasPlayingBeforeEyeRest: Boolean = false

    // Lot 15 (Tâche 4.1) — job du surlignage mot-à-mot courant, remplacé à
    // chaque nouvelle phrase (mécanique delay() sur les wordTimestamps,
    // inchangée par rapport au Lot 14 — seul le déclencheur change).
    private var highlightJob: Job? = null

    // Lot 4, tâche 4.7 — flash différé : pendingHighlightTimeoutJob est la
    // sortie de secours (mise en page qui n'aboutit jamais) ; flashClearJob
    // efface le flash affiché après un court délai. Un seul de chaque à la
    // fois, même discipline que les autres jobs de cet écran.
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
                openPublication(intent.publicationId, targetLocator, intent.flashOnArrival, intent.autoStartTts)
            }
            is ReaderIntent.NextChapter -> navigateToChapter(_state.value.currentChapterIndex + 1)
            is ReaderIntent.PreviousChapter -> navigateToChapter(_state.value.currentChapterIndex - 1)
            is ReaderIntent.JumpToChapter -> navigateToChapter(intent.chapterIndex)
            is ReaderIntent.ToggleToc -> _state.value = _state.value.copy(isTocVisible = !_state.value.isTocVisible)
            is ReaderIntent.PlayCurrentSentence -> playCurrentSentence()
            is ReaderIntent.Pause -> pausePlayback()
            is ReaderIntent.DismissError -> _state.value = _state.value.copy(errorMessage = null)
            is ReaderIntent.DismissVoiceDownloadPrompt -> _state.value = _state.value.copy(showVoiceDownloadPrompt = false)
            is ReaderIntent.RetryOpen -> {
                val id = currentPublicationId
                if (id != null) {
                    _state.value = _state.value.copy(errorMessage = null)
                    openPublication(id, targetLocator = null, flashOnArrival = false)
                }
            }
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
            is ReaderIntent.SaveBookmarkNote -> saveBookmarkNote(intent.note)
            is ReaderIntent.DismissBookmarkNotePrompt -> _state.value = _state.value.copy(pendingBookmarkNoteId = null)
            is ReaderIntent.EditBookmarkNote -> viewModelScope.launch {
                updateBookmarkNote(intent.id, intent.note.trim().ifBlank { null })
            }
            is ReaderIntent.DeleteAnnotation -> viewModelScope.launch { deleteAnnotation(intent.id) }
            is ReaderIntent.UpdateAnnotationNote -> updateAnnotationNote(intent.id, intent.content)
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
            is ReaderIntent.SetReaderMarginStep -> setReaderMarginStep(intent.step)
            is ReaderIntent.SetTextJustified -> setTextJustified(intent.justified)
            is ReaderIntent.SetFontFamily -> setFontFamily(intent.fontFamily)
            is ReaderIntent.SetKeepScreenOn -> setKeepScreenOn(intent.enabled)
            is ReaderIntent.SetAutoScrollSpeed -> setAutoScrollSpeed(intent.speed)
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
        creditVisuallyReadSentences(chapterIndex, sentenceIndex)
        _state.value = _state.value.copy(currentSentenceIndex = sentenceIndex)
        scrollPersistJob?.cancel()
        scrollPersistJob = viewModelScope.launch {
            delay(SCROLL_PERSIST_DEBOUNCE_MS)
            persistPosition(chapterIndex = chapterIndex, sentenceIndex = sentenceIndex)
        }
    }

    /**
     * Chantier statistiques V1 — comptabilise les phrases franchies au
     * défilement manuel, pendant du comptage TTS de [PlaybackOrchestrator].
     *
     * Sans ce chemin, la vitesse de lecture ne serait mesurée que sur les
     * sessions narrées, où elle ne mesure rien d'autre que le débit du
     * synthétiseur. Un lecteur silencieux resterait, lui, à zéro mot à vie.
     *
     * Une **marque haute par chapitre** empêche le recomptage : elle retient la
     * dernière phrase déjà créditée, si bien qu'un aller-retour de défilement,
     * ou la relecture d'un passage, n'ajoute rien. La marque est propre à un
     * chapitre : en changer la réinitialise.
     *
     * Arriver à la phrase `i` signifie avoir terminé les phrases jusqu'à
     * `i - 1` : celle où le regard se pose est en cours de lecture, pas lue.
     *
     * Appelée depuis [updateScrollPosition] seule, qui refuse déjà de
     * s'exécuter pendant le TTS (K3) — les deux chemins ne peuvent donc pas
     * créditer la même phrase.
     */
    private fun creditVisuallyReadSentences(chapterIndex: Int, sentenceIndex: Int) {
        val tracker = sessionTracker ?: return
        val sentences = _state.value.currentChapter?.sentences ?: return

        if (chapterIndex != visualProgressChapterIndex) {
            visualProgressChapterIndex = chapterIndex
            // Position d'entrée dans le chapitre : rien n'y a encore été lu, et
            // ce qui précède relève d'une reprise, pas d'une lecture.
            visualProgressHighWaterMark = _state.value.currentSentenceIndex - 1
        }

        val lastCompleted = sentenceIndex - 1
        if (lastCompleted <= visualProgressHighWaterMark) return

        val from = (visualProgressHighWaterMark + 1).coerceIn(0, sentences.size)
        val to = (lastCompleted + 1).coerceIn(from, sentences.size)
        visualProgressHighWaterMark = lastCompleted

        val crossed = sentences.subList(from, to)
        if (crossed.isEmpty()) return
        tracker.addProgress(words = crossed.sumOf { it.wordCount }, sentences = crossed.size)
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
     *
     * Lot 22, Palier C, tâche 9 — consulte [renderedPageCache] avant tout
     * appel PDFium, écrit après un rendu neuf. La clé porte la résolution
     * (`targetWidthPx`) : un zoom haute définition n'écrase jamais la page
     * au repos.
     */
    suspend fun renderPdfPage(pageIndex: Int, targetWidthPx: Int): RenderedPage? {
        val publicationId = currentPublicationId ?: return fixedPageDocument?.renderPage(pageIndex, targetWidthPx)
        renderedPageCache.get(publicationId, pageIndex, targetWidthPx)?.let { return it }
        val rendered = fixedPageDocument?.renderPage(pageIndex, targetWidthPx) ?: return null
        renderedPageCache.put(publicationId, pageIndex, targetWidthPx, rendered)
        return rendered
    }

    /**
     * Tache 9bis.3.3 — minuteur de sommeil. Un seul job actif a la fois :
     * une nouvelle duree (ou une desactivation) annule tout minuteur en
     * cours, jamais deux qui coexistent.
     */
    private fun setSleepTimer(minutes: Int?) {
        // P2-b — le minuteur appartient à la session, plus à cet écran :
        // s'endormir en écoutant est précisément le cas où le Lecteur est
        // détruit, et un minuteur qui mourrait avec lui laisserait la
        // narration tourner toute la nuit. Cet écran ne fait plus que
        // transmettre l'intention et refléter l'état (collecteur ci-dessus).
        playbackOrchestrator.setSleepTimer(minutes)
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
    private fun openPublication(publicationId: String, targetLocator: Locator? = null, flashOnArrival: Boolean = false, autoStartTts: Boolean = false) {
        // Lot 20 — préchauffe le moteur TTS (chargement des modèles ONNX
        // hors du premier tap de lecture) : dans un process neuf, l'init
        // froide (~10-20 s sur V2206) dépassait le timeout de synthèse de
        // l'ordonnanceur et le moteur retombait définitivement sur la voix
        // système (bug trouvé par la vérification device, corrigé ici et
        // dans FallbackTtsEngine qui ne re-avale plus les annulations).
        viewModelScope.launch(Dispatchers.Default) { ttsEngine.warmUp() }
        // P2-b — cet écran reprend la propriété des ressources de lecture : si
        // une fermeture précédente avait laissé une libération en attente
        // (narration poursuivie sans écran), elle fermerait le résolveur EPUB
        // sous nos pieds à la première pause.
        playbackOrchestrator.releaseOnSessionEnd(null)
        // Lot 12, tache 12.9 — une publication PDF ouverte precedemment
        // garde son FixedPageDocument vivant jusqu'ici (decision actee 14
        // du plan) ; en ouvrir une nouvelle doit d'abord fermer l'ancien,
        // jamais accumuler des handles natifs non fermes.
        fixedPageDocument?.close()
        fixedPageDocument = null
        // Correctif Lot 21 — un `pendingBookmarkNoteId` laissé par une
        // session précédente (Snackbar ignoré/expiré avant sa résolution,
        // ou fermeture du lecteur en plein milieu) ne doit jamais survivre
        // à l'ouverture d'une AUTRE publication : `saveBookmarkNote`
        // écrirait alors sur l'id d'un signet qui n'a plus de sens ici.
        _state.value = _state.value.copy(isFixedPageReady = false, pendingBookmarkNoteId = null)
        viewModelScope.launch {
            val publication = publicationRepository.getById(publicationId) ?: run {
                Log.w("ReaderViewModel", "openPublication: publication introuvable ($publicationId)")
                _state.value = _state.value.copy(errorMessage = "Publication introuvable.")
                return@launch
            }
            when (val result = publicationParser.parse(publication.fileUri)) {
                is ParseResult.Success -> {
                    currentPublicationId = publicationId

                    // Lot 17, tâche 1 — écrit lastOpened à l'ouverture : la
                    // carte « Reprendre la lecture » (LibraryUiState) dérive de
                    // ce champ, jamais écrit jusqu'ici (code mort).
                    publicationRepository.setLastOpened(publicationId, System.currentTimeMillis())

                    // ───── Lot Sessions : démarre le tracking ─────
                    // P2-b — si une narration sans écran suivait déjà ce livre,
                    // on REPREND son tracker au lieu d'en ouvrir un second :
                    // deux trackers concurrents produiraient des fragments qui
                    // se chevauchent, donc du temps compté deux fois.
                    val handover = narrationSessionContinuation.takeOver(publicationId)
                    val tracker = handover?.tracker ?: ReadingSessionTracker(publicationId)
                    sessionTracker = tracker
                    lastFragmentSavedMs = handover?.lastFragmentSavedMs ?: tracker.startTimestamp
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
                        title = publication.title.cleanedForDisplay(),
                        author = publication.authors.cleanedAuthorsForDisplay().ifBlank { null },
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
                        readerMarginStep = prefs.readerMarginStep,
                        isTextJustified = prefs.textJustified,
                        keepScreenOn = prefs.keepScreenOn,
                        // Lot 12, tache 12.9 — jamais reporte avant ce lot.
                        publicationFormat = publication.format,
                        pageOffsetY = restored?.locator?.pageOffsetY ?: 0f,
                        // Plan v3, Palier 3.6 — ID de publication + résolveur images EPUB
                        publicationId = publicationId,
                        epubResourceResolver = if (publication.format == PublicationFormat.EPUB) {
                            this@ReaderViewModel.epubResourceResolver
                        } else null,
                    )
                    // P1 — alimente les métadonnées de la notification média
                    // (titre/auteur), source unique : jamais rechargées depuis
                    // un repository dans le service.
                    playbackOrchestrator.setMetadata(
                        publicationId = publication.id,
                        title = publication.title.cleanedForDisplay(),
                        author = publication.authors.cleanedAuthorsForDisplay().ifBlank { null },
                        coverUri = publication.coverUri,
                    )
                    // Plan v3, Palier 3.6 — initialiser le parsing lazy.
                    // Correctif : `registerPublication` était réservé à
                    // l'EPUB, alors que `CompositeChapterParser` l'enregistre
                    // déjà des DEUX côtés par construction (son KDoc : « le
                    // format n'est pas connu ici »). Un PDF ouvert une
                    // deuxième fois restaurait une position au-delà des 5
                    // pages sondées à l'import (`PdfPublicationParser.
                    // PROBE_PAGES`) et `PdfChapterParser.parseChapter`
                    // échouait alors avec `IllegalStateException` (`fileUris`
                    // jamais renseigné pour ce publicationId) — rattrapée en
                    // "Impossible de charger ce chapitre." par
                    // `loadChapterContentIfNeeded`. Le TXT n'est pas
                    // concerné (chapitre unique déjà entièrement chargé à
                    // l'import), mais l'enregistrer ne coûte rien de plus
                    // qu'un `put` en mémoire.
                    chapterParser.registerPublication(publicationId, publication.fileUri)
                    if (publication.format == PublicationFormat.EPUB) {
                        try {
                            epubResourceResolver.open(publicationId, publication.fileUri)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Best-effort : le texte (chapterParser) peut encore
                            // s'ouvrir même si la résolution d'images échoue —
                            // ne jamais faire planter l'ouverture du lecteur pour
                            // un chapitre potentiellement illustré (K2/K6).
                            Log.w("ReaderViewModel", "openPublication: echec ouverture resolveur images", e)
                        }
                    }
                    // Bug réel trouvé sur appareil (session Plan v3) : sans cet
                    // appel, le chapitre COURANT à l'ouverture n'était jamais
                    // chargé — preloadAdjacentChapters ne précharge que N-1/N+1/
                    // N+2, jamais N lui-même. Le lecteur s'ouvrait donc sur une
                    // zone de lecture vide (coquille ChapterContent.Rich sans
                    // blocks) jusqu'à ce que l'utilisateur navigue et revienne.
                    val chapterLoadJob = loadChapterContentIfNeeded(_state.value.currentChapterIndex)
                    preloadAdjacentChapters(_state.value.currentChapterIndex)
                    observeAnnotations(publicationId)
                    observeBookmarks(publicationId)
                    if (prefs.eyeRestReminderEnabled) scheduleEyeRestReminder(prefs.eyeRestReminderIntervalMinutes)
                    // Restauration de position : si aucun locator externe n'est fourni
                    // (ouverture depuis la bibliothèque), on utilise le locator sauvegardé
                    // pour revenir à la position exacte. Bug réel corrigé ici : sans
                    // attendre `chapterLoadJob`, `chapters[i].sentences` était encore
                    // vide au moment de naviguer — navigateToLocator retombait sur la
                    // phrase 0 (indexOfFirst introuvable → coerceAtLeast(0)) ET
                    // persistait cette fausse position 0, écrasant la vraie position
                    // sauvegardée à chaque réouverture. Les autres appels ci-dessus
                    // (préchargement, observateurs, minuteur) restent lancés en
                    // parallèle, seule cette section attend le chapitre courant.
                    val effectiveLocator = targetLocator ?: restored?.locator
                    viewModelScope.launch {
                        chapterLoadJob?.join()
                        if (effectiveLocator != null) navigateToLocator(effectiveLocator, flashOnArrival)
                        if (autoStartTts) {
                            playCurrentSentence()
                        }
                    }

                    // Lot 12, tache 12.9 — ouvre le document de rendu fixe
                    // pour toute la session de lecture (decision actee 14),
                    // apres avoir peuple l'etat pour que errorMessage
                    // s'affiche sur le meme ecran en cas d'echec.
                    if (publication.format == PublicationFormat.PDF) {
                        when (val openResult = fixedPageRenderer.open(publication.fileUri)) {
                            is FixedPageOpenResult.Success -> {
                                fixedPageDocument = openResult.document
                                // Reveille l'effet de rendu de FixedPageContent,
                                // qui a deja pu demander sa page a vide.
                                _state.value = _state.value.copy(isFixedPageReady = true)
                            }
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
        // Lot 21, tâche 6 — les blocs du chapitre alimentent le
        // `paragraphIndex` des Locators (renfort, `charOffset` reste
        // l'ancre de vérité).
        val blocks = (chapter.content as? ChapterContent.Rich)?.blocks.orEmpty()
        val (startLocator, endLocator) = annotationSelectionHandler.resolveCharRange(
            freeRange.first, endOffsetExclusive, chapter.index, chapter.href, blocks,
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
            // Lot 22, tâche 12 — mémorise la couleur pour la proposer en
            // tête du sélecteur au prochain surlignage.
            val prefs = preferencesRepository.get()
            preferencesRepository.update(prefs.copy(recentAnnotationColors = prefs.recentAnnotationColors.withRecentColor(color)))
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
                    val bookmark = Bookmark(
                        id = UUID.randomUUID().toString(),
                        publicationId = publicationId,
                        locator = Locator(
                            resourceHref = "page-$chapterIndex",
                            chapterIndex = chapterIndex,
                            charOffset = 0,
                            pageOffsetY = _state.value.pageOffsetY,
                        ),
                        // Correctif Lot 21 — `title` était laissé nul sur
                        // PDF, seule la branche EPUB le remplissait :
                        // `BookmarkPanel` retombait alors sur
                        // "Chapitre ${chapterIndex + 1}", faux pour un PDF
                        // où `chapterIndex` est un index de PAGE.
                        title = "Page ${chapterIndex + 1}",
                        excerpt = "Page ${chapterIndex + 1}",
                        createdAt = System.currentTimeMillis(),
                    )
                    createBookmark(bookmark)
                    // Lot 21, tâche 5 — note optionnelle proposée après la
                    // création (jamais un dialogue bloquant : le signet est
                    // déjà créé, l'utilisateur peut fermer sans note).
                    _state.value = _state.value.copy(pendingBookmarkNoteId = bookmark.id)
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
                val bookmark = Bookmark(
                    id = UUID.randomUUID().toString(),
                    publicationId = publicationId,
                    locator = sentence.startLocator(chapterIndex = chapter.index, resourceHref = chapter.href),
                    // Lot 21, tâche 5 — le signet porte un titre lisible
                    // (début de la phrase) au lieu de rester sans titre ;
                    // la note optionnelle est proposée après la création.
                    // Correctif — ellipse explicite quand le titre est
                    // réellement tronqué (coupe en plein mot sinon).
                    title = sentence.text.truncateWithEllipsis(BOOKMARK_TITLE_MAX_CHARS),
                    excerpt = sentence.text.take(Bookmark.MAX_EXCERPT_LENGTH),
                    createdAt = System.currentTimeMillis(),
                )
                createBookmark(bookmark)
                _state.value = _state.value.copy(pendingBookmarkNoteId = bookmark.id)
            }
        }
    }

    /**
     * Lot 21, tâche 5 — pose la note optionnelle sur le signet en attente
     * (`pendingBookmarkNoteId`), puis referme le dialogue. `note` vide ou
     * blanche = note nulle, le signet reste valide (jamais un signet
     * invalide).
     */
    private fun saveBookmarkNote(note: String) {
        val bookmarkId = _state.value.pendingBookmarkNoteId ?: return
        _state.value = _state.value.copy(pendingBookmarkNoteId = null)
        // Correctif Lot 21 — le signet peut avoir été supprimé entre la
        // création et la résolution du Snackbar (un autre chemin : le
        // panneau de signets, une synchronisation distante) ; écrire quand
        // même produirait un `UPDATE` silencieux sur une ligne inexistante.
        if (_state.value.bookmarks.none { it.id == bookmarkId }) return
        viewModelScope.launch {
            updateBookmarkNote(bookmarkId, note.trim().ifBlank { null })
        }
    }

    /**
     * Lot 22, tâche 11 — édition de note depuis `BookmarkPanel` (onglet
     * Notes). Préserve tous les autres champs de l'annotation ; met à
     * jour `updatedAt`. Sans effet si l'annotation a été supprimée entre
     * l'affichage du panneau et la confirmation (même garde que
     * [saveBookmarkNote]).
     */
    private fun updateAnnotationNote(id: String, content: String?) {
        val existing = _state.value.annotations.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            updateAnnotation(existing.copy(content = content, updatedAt = System.currentTimeMillis()))
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
        preloadAdjacentChapters(locator.chapterIndex)
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
            ?.sentences?.getOrNull(target.sentenceIndex)
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
        preloadAdjacentChapters(targetIndex)
        if (wasPlaying) playCurrentSentence()
    }

    /**
     * Plan v3, Palier 3.6 — charge le contenu d'un chapitre Rich vide
     * via [ChapterParser.parseChapter]. Les chapitres Legacy (PDF/TXT)
     * ou déjà chargés sont ignorés.
     *
     * Retourne le [Job] du parsing lancé, ou `null` si rien à charger
     * (déjà en cache) — permet à l'appelant d'attendre la fin du
     * chargement uniquement quand c'est nécessaire (restauration de
     * position à l'ouverture), sans bloquer les autres appelants qui
     * l'utilisent en tir-et-oublie (changement de chapitre manuel).
     */
    private fun loadChapterContentIfNeeded(chapterIndex: Int): Job? {
        val chapter = _state.value.chapters.getOrNull(chapterIndex) ?: return null
        val rich = chapter.content as? ChapterContent.Rich ?: return null
        if (rich.blocks.isNotEmpty()) return null // déjà chargé

        val publicationId = currentPublicationId ?: return null
        return viewModelScope.launch {
            // Un href introuvable (K6 : encodage divergent entre le spine
            // et la ressource, ou EPUB malformé) ne doit jamais crasher le
            // lecteur — juste laisser ce chapitre vide et le signaler.
            val richChapter = try {
                chapterParser.parseChapter(publicationId, chapter.href)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ReaderViewModel", "loadChapterContentIfNeeded: echec chapitre ${chapter.href}", e)
                _state.value = _state.value.copy(errorMessage = "Impossible de charger ce chapitre.")
                return@launch
            }
            val chapters = _state.value.chapters.toMutableList()
            // Bug réel trouvé à l'audit (livres à couverture prépendue, ex.
            // "L'Arcane des Épées") : EpubChapterParser recalcule
            // `chapterIndex` depuis `publication.readingOrder.indexOf(link)`,
            // sans connaître le décalage +1 appliqué par
            // ReadiumPublicationParser.parseLazy quand une couverture est
            // insérée en tête. `richChapter.index` peut donc diverger de sa
            // position réelle dans `chapters` — invisible pour la pagination
            // (auto-cohérente, toujours indexée par `chapter.index`), mais
            // fatal pour le surlignage : `confirmAnnotation` sauvegarde le
            // Locator avec ce `chapter.index` faux, alors que le rendu
            // (BookBlockItem/PagedChapterContent) filtre par
            // `state.currentChapterIndex` (la position, correcte) — les deux
            // ne matchent jamais, la couleur ne s'affiche donc jamais.
            // Seule source de vérité désormais : la position dans `chapters`.
            chapters[chapterIndex] = richChapter.copy(index = chapterIndex)
            _state.value = _state.value.copy(chapters = chapters)
        }
    }

    /**
     * Plan v4 — lance le préchargement asynchrone des chapitres adjacents
     * (N+1 prioritaire, N-1, N+2). Annule les préchargements précédents
     * (anti-starvation). Les chapitres déjà chargés (Rich avec blocks non
     * vides) sont sautés. Fire-and-forget : pas de joinAll.
     */
    private fun preloadAdjacentChapters(centerIndex: Int) {
        preloadScope?.coroutineContext[Job]?.cancel()
        val publicationId = currentPublicationId ?: return
        val chapters = _state.value.chapters
        preloadScope = CoroutineScope(
            viewModelScope.coroutineContext + Job(viewModelScope.coroutineContext[Job]),
        )
        // N+1 en premier (direction de lecture probable), puis N-1, puis N+2
        val targets = sequenceOf(centerIndex + 1, centerIndex - 1, centerIndex + 2)
        preloadScope?.launch {
            for (idx in targets) {
                val chapter = chapters.getOrNull(idx) ?: continue
                val rich = chapter.content as? ChapterContent.Rich
                if (rich != null && rich.blocks.isNotEmpty()) continue // déjà chargé
                chapterParser.preload(publicationId, chapter.href, this)
            }
        }
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
            // Bug réel trouvé au diagnostic : reconstruire un ReadingState
            // « nu » ici écrasait silencieusement overrides/voiceProfileId
            // déjà enregistrés à chaque scroll ou phrase TTS — préserver
            // l'existant, comme setOverrides le fait déjà pour son propre champ.
            val existing = getReadingState(publicationId)
            updateReadingState(
                ReadingState(
                    publicationId = publicationId,
                    locator = locator,
                    lastReadAt = System.currentTimeMillis(),
                    voiceProfileId = existing?.voiceProfileId,
                    overrides = existing?.overrides,
                ),
            )
        }
    }

    /**
     * Positionne l'affichage sur la premiere page porteuse de texte a partir
     * de la page courante, en les chargeant paresseusement une a une.
     *
     * @return `false` si aucune page en aval n'a de texte dans la fenetre
     *   exploree — il n'y a alors rien a narrer, et la lecture ne demarre pas.
     *
     * Fenetre bornee ([MAX_EMPTY_PAGE_LOOKAHEAD]) : sur un PDF entierement
     * scanne, parcourir 994 pages pour ne rien trouver bloquerait le geste
     * de l'utilisateur sans rien lui apprendre de plus.
     */
    private suspend fun advanceToFirstNarratablePage(): Boolean {
        val start = _state.value.currentChapterIndex
        val last = minOf(_state.value.chapters.lastIndex, start + MAX_EMPTY_PAGE_LOOKAHEAD)
        for (index in start..last) {
            loadChapterContentIfNeeded(index)?.join()
            if (_state.value.chapters.getOrNull(index)?.sentences?.isNotEmpty() == true) {
                if (index != start) {
                    _state.value = _state.value.copy(currentChapterIndex = index, currentSentenceIndex = 0)
                }
                return true
            }
        }
        return false
    }

    /**
     * A.1 (Lot 15, Tâche 4.1) — délègue la lecture continue au
     * [PlaybackOrchestrator] (producteur/consommateur gapless). Le ViewModel
     * ne fait plus la synthèse ni la boucle : il résout le profil vocal et
     * passe la main. Le surlignage et l'auto-avance sont pilotés par l'état
     * de l'ordonnanceur (voir init).
     */
    private fun playCurrentSentence() {
        // ADR-017 volet 2 — sur un PDF, la page courante peut ne porter aucun
        // texte (planche scannee, illustration pleine page). Depuis le
        // passage au parsing paresseux, `sentences` vide ne suffit PLUS a le
        // conclure : la page peut simplement ne pas encore etre chargee. Il
        // faut donc la charger avant de decider, ce qui suspend — d'ou le
        // detour par une coroutine, contrairement au reste de cette methode.
        //
        // Abandon silencieux si rien n'est narrable dans la fenetre exploree :
        // `errorMessage` n'est PAS une notification, il remplace tout l'ecran
        // de lecture par un `ErrorState` a deux boutons (ReaderScreen ~391).
        // Le cas « aucune page du livre n'a de texte » est deja traite en
        // amont — `ReaderUiState.supportsTts` masque les commandes TTS — donc
        // n'arrive ici qu'un livre dont une longue section est scannee.
        if (_state.value.publicationFormat == PublicationFormat.PDF) {
            viewModelScope.launch {
                if (advanceToFirstNarratablePage()) startNarrationAtCurrentPosition()
            }
            return
        }
        startNarrationAtCurrentPosition()
    }

    /**
     * Demarre la narration a la position courante, celle-ci etant tenue pour
     * narrable — c'est a l'appelant de s'en etre assure (voir
     * [advanceToFirstNarratablePage] pour le PDF).
     */
    private fun startNarrationAtCurrentPosition() {
        val chapter = _state.value.currentChapter ?: return
        val sentences = chapter.sentences
        if (sentences.isEmpty()) return
        val publicationId = currentPublicationId ?: return

        // ───── Lot Sessions : bascule en mode TTS ─────
        sessionTracker?.switchMode(DomainReadingMode.AUDIO)
        // ───── Fin Lot Sessions ─────

        _state.value = _state.value.copy(isPlaying = true, isAudioActive = false)
        val startFrom = _state.value.currentSentenceIndex

        // P2-b — donne à l'ordonnanceur de quoi enchaîner seul les chapitres
        // suivants, y compris si cet écran est détruit entre-temps. Reposé à
        // chaque lancement : la liste des chapitres peut avoir changé (contenu
        // Rich chargé paresseusement), et l'href reste stable, lui.
        playbackOrchestrator.setNarrationProgram(
            publicationId = publicationId,
            chapterHrefs = _state.value.chapters.map { it.href },
            skipEmptyChapters = _state.value.publicationFormat == PublicationFormat.PDF,
        )

        viewModelScope.launch {
            val prefs = preferencesRepository.get()
            val voiceProfile = resolveVoiceProfile(prefs)
            playbackOrchestrator.play(
                sentences = sentences,
                voiceProfile = voiceProfile,
                startFrom = startFrom,
                publicationId = publicationId,
                chapterIndex = chapter.index,
                resourceHref = chapter.href,
            )
        }
    }

    /**
     * Repli `delay()` du surlignage mot-à-mot (mécanique inchangée depuis le
     * Lot 14), déclenché uniquement quand la position réelle est invalide
     * (Lot 16, Tâche 2.2). Annule et remplace le job précédent.
     */
    private fun startWordHighlight(timestamps: List<WordTimestamp>) {
        highlightJob?.cancel()
        if (timestamps.isEmpty()) {
            _state.value = _state.value.copy(highlightedWordRange = null)
            return
        }
        highlightJob = viewModelScope.launch {
            timestamps.forEach { wt ->
                _state.value = _state.value.copy(
                    highlightedWordRange = wt.charOffset until (wt.charOffset + wt.word.length),
                )
                delay((wt.endMs - wt.startMs).coerceAtLeast(0L))
            }
            _state.value = _state.value.copy(highlightedWordRange = null)
        }
    }

    /**
     * Lot 10 (restauré au Lot 20) — proposition proactive de la voix
     * neuronale au premier usage réel du TTS. Déclenchée quand
     * l'ordonnanceur passe en lecture : la première synthèse a eu lieu,
     * donc `ttsEngine.id` reflète le moteur réellement actif, repli
     * compris (voir FallbackTtsEngine). Si le moteur actif est la voix
     * du système, le modèle neuronal n'est pas installé → proposition.
     */
    private fun checkVoiceDownloadPrompt() {
        if (ttsEngine.id != TtsEngineId.ANDROID_NATIVE) return
        viewModelScope.launch {
            val prefs = preferencesRepository.get()
            if (!prefs.hasPromptedVoiceDownload) {
                _state.value = _state.value.copy(showVoiceDownloadPrompt = true)
                preferencesRepository.update(prefs.copy(hasPromptedVoiceDownload = true))
            }
        }
    }

    /**
     * Fin naturelle d'un chapitre (l'ordonnanceur passe Idle alors que la
     * lecture était engagée) : auto-avance au chapitre suivant et reprend,
     * ou s'arrête en fin de livre.
     */
    /**
     * P2-b — recale l'affichage sur le chapitre que l'ordonnanceur a enchaîné
     * de lui-même (auto-avance).
     *
     * Distinct de [navigateToChapter] sur trois points, et c'est toute la
     * différence : ne coupe pas la lecture (elle est déjà en cours sur le
     * chapitre cible), ne relance pas `playCurrentSentence` (ce serait rejouer
     * depuis le début ce que l'ordonnanceur joue déjà), et ne persiste pas la
     * position — pendant la narration, l'ordonnanceur en est le seul écrivain
     * (K3, chemins TTS et manuel jamais simultanés).
     */
    private fun syncDisplayToNarratedChapter(targetIndex: Int) {
        if (targetIndex !in _state.value.chapters.indices) return
        _state.value = _state.value.copy(
            currentChapterIndex = targetIndex,
            currentSentenceIndex = 0,
            highlightedWordRange = null,
        )
        loadChapterContentIfNeeded(targetIndex)
        preloadAdjacentChapters(targetIndex)
    }

    /**
     * Interrompt réellement la lecture en cours : arrête l'ordonnanceur
     * (le pipeline producteur/consommateur est annulé et la file du lecteur
     * vidée). `isPlaying` repasse à faux avant l'arrêt — l'état Idle qui
     * suit est un arrêt volontaire, pas une fin naturelle de chapitre.
     */
    private fun pausePlayback() {
        _state.value = _state.value.copy(isPlaying = false, isAudioActive = false, highlightedWordRange = null)
        highlightJob?.cancel()
        playbackOrchestrator.stop()

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

    /**
     * P4 — cran de marge latérale. Borné ici plutôt que laissé au `require()`
     * du domaine : un cran hors bornes venant de l'UI est un défaut de
     * l'appelant, pas une donnée utilisateur à faire planter l'app.
     */
    private fun setReaderMarginStep(step: Int) {
        val bounded = step.coerceIn(UserPreferences.MARGIN_STEP_RANGE)
        viewModelScope.launch {
            val current = preferencesRepository.get()
            preferencesRepository.update(current.copy(readerMarginStep = bounded))
        }
    }

    /** P4 — justification du texte (césure comprise, jamais l'une sans l'autre). */
    private fun setTextJustified(justified: Boolean) {
        viewModelScope.launch {
            val current = preferencesRepository.get()
            preferencesRepository.update(current.copy(textJustified = justified))
        }
    }

    /**
     * Correctif Lot 21 — sélecteur de police du panneau de réglages du
     * Lecteur (`ReaderSettingsPanel`). La police fait partie
     * d'`effectiveSettings` : sans le recalcul ci-dessous, la préférence
     * était persistée mais jamais appliquée au rendu (le collect
     * preferences n'émettant pas `effectiveSettings`), et le sélecteur
     * paraissait mort — le même recalcul que `setOverrides`, sans
     * toucher aux surcharges par publication.
     */
    private fun setFontFamily(fontFamily: FontFamily) {
        viewModelScope.launch {
            val current = preferencesRepository.get()
            preferencesRepository.update(current.copy(fontFamily = fontFamily))
            val overrides = _state.value.currentOverrides
            val effectiveSettings = EffectiveReadingSettings.resolve(overrides, preferencesRepository.get())
            _state.value = _state.value.copy(effectiveSettings = effectiveSettings)
        }
    }

    /** P4 — maintien de l'écran allumé, appliqué à la fenêtre par ReaderScreen. */
    private fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            val current = preferencesRepository.get()
            preferencesRepository.update(current.copy(keepScreenOn = enabled))
        }
    }

    /**
     * Lot 21, tâche 9 — vitesse d'auto-scroll visuel (0 = désactivé).
     * Bornée dans le domaine, comme setReaderMarginStep : un cran hors
     * bornes venant de l'UI est un défaut de l'appelant.
     */
    private fun setAutoScrollSpeed(speed: Int) {
        val bounded = speed.coerceIn(UserPreferences.AUTO_SCROLL_SPEED_RANGE)
        viewModelScope.launch {
            val current = preferencesRepository.get()
            preferencesRepository.update(current.copy(autoScrollSpeed = bounded))
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
     * quand le ViewModel est détruit.
     *
     * P1-d — la narration en cours **survit** désormais à la destruction de
     * l'écran : quitter le Lecteur pour la bibliothèque ne coupe plus la voix,
     * puisque la session appartient au service foreground et à sa notification
     * (`AudioPlaybackService`), pas à cet écran. L'ordonnanceur est un
     * `@Singleton` : il garde son contexte de session (phrases, voix,
     * publication) et continue de persister la position lue.
     *
     * L'arrêt reste inconditionnel quand rien n'est engagé — sinon un segment
     * résiduel continuerait de jouer sans notification pour le contrôler, ce
     * que corrigeait la Tâche A.2 à l'origine.
     */
    override fun onCleared() {
        // ───── Lot Sessions : sauvegarde finale + arrêt timer ─────
        saveCurrentFragment()
        checkpointJob?.cancel()
        // ───── Fin Lot Sessions ─────

        // Bug réel trouvé au diagnostic : `scrollPersistJob?.cancel()` /
        // `pageOffsetPersistJob?.cancel()` plus bas annulaient un débounce de
        // position encore en attente (SCROLL_PERSIST_DEBOUNCE_MS) SANS écrire
        // la position qu'il portait — fermer le livre juste après un
        // défilement ou un geste PDF perdait ce dernier déplacement, la
        // reprise retombant sur la position précédente. Même remède que
        // `saveCurrentFragment` ci-dessus : flush immédiat AVANT
        // `super.onCleared()`, tant que `viewModelScope` est encore actif.
        if (scrollPersistJob?.isActive == true) {
            scrollPersistJob?.cancel()
            persistPosition(chapterIndex = _state.value.currentChapterIndex, sentenceIndex = _state.value.currentSentenceIndex)
        }
        if (pageOffsetPersistJob?.isActive == true) {
            pageOffsetPersistJob?.cancel()
            persistPosition(chapterIndex = _state.value.currentChapterIndex, sentenceIndex = 0)
        }

        super.onCleared()
        val sessionEngaged = playbackOrchestrator.isSessionEngaged()
        if (!sessionEngaged) playbackOrchestrator.stop()
        scrollPersistJob?.cancel()
        eyeRestReminderJob?.cancel()
        eyeRestCountdownJob?.cancel()
        // Lot 12, tache 12.9 — libere les ressources natives PDFium du
        // renderer, jamais laisse au ramasse-miettes (decision actee 14).
        pageOffsetPersistJob?.cancel()
        fixedPageDocument?.close()
        fixedPageDocument = null
        // Plan v4 — libère le cache LRU du ChapterParser + ferme le resolver EPUB
        preloadScope?.coroutineContext[Job]?.cancel()
        // P2-b — sauf si la narration continue sans cet écran : l'ordonnanceur
        // parse lui-même le chapitre suivant à l'auto-avance, ce qui exige que
        // le cache ET le résolveur de ressources EPUB restent ouverts. Les
        // libérer ici couperait la narration au premier changement de chapitre,
        // avec une erreur de ressource introuvable au lieu d'un arrêt propre.
        // Ils sont alors libérés à la fermeture réelle de la session
        // (`PlaybackServiceLauncher`, quand `sessionState` retombe à IDLE).
        if (sessionEngaged) {
            // P2-b — cède le tracker au relais : l'écoute qui continue sans cet
            // écran doit rester comptabilisée. `saveCurrentFragment()` vient de
            // flusher, `lastFragmentSavedMs` borne donc correctement le premier
            // fragment que le relais posera.
            sessionTracker?.let {
                narrationSessionContinuation.continueTracking(it, lastFragmentSavedMs)
            }
            // Capture volontairement restreinte au parseur, au résolveur et à
            // l'identifiant : aucune référence à ce ViewModel, qui doit rester
            // collectable pendant que la narration se poursuit sans lui.
            val parser = chapterParser
            val resolver = epubResourceResolver
            val publicationId = currentPublicationId
            playbackOrchestrator.releaseOnSessionEnd {
                publicationId?.let { parser.invalidate(it) }
                resolver.close()
            }
        } else {
            currentPublicationId?.let { chapterParser.invalidate(it) }
            epubResourceResolver.close()
            playbackOrchestrator.clearNarrationProgram()
        }
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
        // P1 (plan polissage Pareto) — ne pauser le tracker que si aucune
        // écoute TTS n'est active : le temps écoulé écran éteint pendant la
        // narration doit rester imputé à l'AUDIO, pas figé. Sinon l'écoute
        // en arrière-plan n'est jamais comptabilisée dans les statistiques.
        if (!_state.value.isPlaying) {
            sessionTracker?.pause()
        }
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

    /**
     * Exposé pour les tests de [onAppBackground]/[onAppForeground] : le
     * tracker de session est privé, mais son état `paused` détermine si le
     * temps d'écoute en arrière-plan est comptabilisé ou figé. `true` par
     * défaut quand aucune publication n'est ouverte (rien à suivre).
     */
    @VisibleForTesting
    internal fun isSessionTrackerPausedForTest(): Boolean = sessionTracker?.isPaused ?: true

    /**
     * Mots crédités à la session en cours — exposé pour vérifier le comptage
     * sans attendre qu'un fragment soit persisté (il faut 5 s d'activité).
     */
    @VisibleForTesting
    internal fun sessionWordsReadForTest(): Int = sessionTracker?.wordsRead ?: 0

    /** Timer de checkpoint : sauve un fragment toutes les 5 minutes. */
    private fun startCheckpointTimer() {
        checkpointJob?.cancel()
        checkpointJob = viewModelScope.launch {
            while (true) {
                delay(CHECKPOINT_INTERVAL_MS)
                val t = sessionTracker ?: continue
                if (t.isPaused) continue
                val snapshot = t.snapshot()
                if (snapshot.totalMs < 5_000L) continue
                persistFragment(snapshot)
                t.reset()
            }
        }
    }

    /** Flush + save d'un fragment sans pauser le tracker. */
    private fun saveCurrentFragment() {
        val t = sessionTracker ?: return
        val snapshot = t.snapshot()
        if (snapshot.totalMs < 5_000L) return
        persistFragment(snapshot)
        t.reset()
    }

    /**
     * Insère un fragment de session en base (Dispatchers.IO).
     * Chaque fragment est non-chevauchant : [lastFragmentSavedMs]
     * avance à chaque sauvegarde.
     */
    private fun persistFragment(snapshot: TrackerSnapshot) {
        val fragmentStart = lastFragmentSavedMs
        lastFragmentSavedMs = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            readingSessionRepository.insert(
                ReadingSession(
                    id = java.util.UUID.randomUUID().toString(),
                    publicationId = sessionTracker!!.publicationId,
                    startedAt = fragmentStart,
                    endedAt = lastFragmentSavedMs,
                    mode = if (snapshot.ttsMs >= snapshot.visualMs) DomainReadingMode.AUDIO
                    else DomainReadingMode.VISUAL,
                    sentencesRead = snapshot.sentences,
                    wordsRead = snapshot.words,
                    visualDurationMs = snapshot.visualMs,
                    ttsDurationMs = snapshot.ttsMs,
                )
            )
        }
    }

    companion object {
        /** Intervalle de checkpoint : 5 minutes. */
        private const val CHECKPOINT_INTERVAL_MS = 5 * 60 * 1000L

        /** Pages sans texte tolerees avant de renoncer a narrer un PDF. */
        private const val MAX_EMPTY_PAGE_LOOKAHEAD = 20

        /**
         * Lot 21, tâche 5 — longueur max du titre lisible d'un signet
         * (début de la phrase), affiché en gras dans `BookmarkPanel`.
         */
        private const val BOOKMARK_TITLE_MAX_CHARS = 60
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

/**
 * Correctif Lot 21 — coupe [maxLength] caractères en ajoutant une
 * ellipse `…` quand [this] est réellement tronqué, plutôt qu'une coupure
 * brute en plein mot indiscernable d'un texte court.
 */
private fun String.truncateWithEllipsis(maxLength: Int): String =
    if (length <= maxLength) this else take((maxLength - 1).coerceAtLeast(0)) + "…"

/** A.5 — repli quand `UserPreferences.activeVoiceProfileId` est `null`. */
private val DEFAULT_VOICE_PROFILE = VoiceProfile(
    id = "vp-native-fr", engine = TtsEngineId.ANDROID_NATIVE,
    voice = "fr-fr-default", language = "fr-FR",
)
