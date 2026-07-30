package com.inktone.feature.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Bookmark
import com.inktone.domain.model.EffectiveReadingSettings
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.SleepTimerState
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.repository.AnnotationRepository
import com.inktone.domain.repository.BookmarkRepository
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationParser
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.usecase.AddAnnotationUseCase
import com.inktone.domain.usecase.CreateBookmarkUseCase
import com.inktone.domain.usecase.DeleteBookmarkUseCase
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import com.inktone.domain.valueobject.Locator
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val annotationRepository: AnnotationRepository,
    private val addAnnotation: AddAnnotationUseCase,
    private val bookmarkRepository: BookmarkRepository,
    private val createBookmark: CreateBookmarkUseCase,
    private val deleteBookmark: DeleteBookmarkUseCase,
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
                _state.value = _state.value.copy(isReadingRulerEnabled = preferences.readingRulerEnabled)
            }
        }
    }

    private var currentPublicationId: String? = null
    private val chapterPreloader = ChapterPreloader(viewModelScope)
    private val sentenceAudioBuffer = SentenceAudioBuffer(viewModelScope, ttsEngine)
    private val annotationSelectionHandler = AnnotationSelectionHandler()
    private var sleepTimerJob: Job? = null

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
                openPublication(intent.publicationId, targetLocator)
            }
            is ReaderIntent.BootstrapAndOpenFixture -> bootstrapAndOpenFixture(intent.publicationId, intent.fileUri)
            is ReaderIntent.NextChapter -> navigateToChapter(_state.value.currentChapterIndex + 1)
            is ReaderIntent.PreviousChapter -> navigateToChapter(_state.value.currentChapterIndex - 1)
            is ReaderIntent.JumpToChapter -> navigateToChapter(intent.chapterIndex)
            is ReaderIntent.ToggleToc -> _state.value = _state.value.copy(isTocVisible = !_state.value.isTocVisible)
            is ReaderIntent.PlayCurrentSentence -> playCurrentSentence()
            is ReaderIntent.Pause -> _state.value = _state.value.copy(isPlaying = false)
            is ReaderIntent.BeginSentenceSelection -> _state.value = _state.value.copy(
                selectionAnchorIndex = intent.sentenceIndex, selectionFocusIndex = intent.sentenceIndex,
            )
            is ReaderIntent.ExtendSentenceSelection -> _state.value = _state.value.copy(selectionFocusIndex = intent.sentenceIndex)
            is ReaderIntent.ClearSentenceSelection -> _state.value = _state.value.copy(
                selectionAnchorIndex = null, selectionFocusIndex = null,
            )
            is ReaderIntent.ConfirmAnnotation -> confirmAnnotation(intent.color)
            is ReaderIntent.CreateBookmark -> createBookmarkAtCurrentPosition()
            is ReaderIntent.ToggleBookmarkList -> _state.value = _state.value.copy(
                isBookmarkListVisible = !_state.value.isBookmarkListVisible,
            )
            is ReaderIntent.DeleteBookmark -> viewModelScope.launch { deleteBookmark(intent.id) }
            is ReaderIntent.NavigateToLocator -> navigateToLocator(intent.locator)
            is ReaderIntent.SetOverrides -> setOverrides(intent.overrides)
            is ReaderIntent.SetSleepTimer -> setSleepTimer(intent.minutes)
        }
    }

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
            _state.value = _state.value.copy(isPlaying = false, sleepTimer = null)
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
            _state.value = _state.value.copy(
                currentOverrides = overrides,
                effectiveSettings = EffectiveReadingSettings.resolve(overrides, preferencesRepository.get()),
            )
        }
    }

    /**
     * Ouvre une publication déjà importée : récupère son `fileUri` via
     * le repository, parse le contenu (CompositePublicationParser),
     * puis restaure la dernière position connue (K3) si elle existe.
     * Les cas d'erreur de parsing (Corrompu, DRM, format non supporté)
     * ne sont pas encore reflétés dans `ReaderUiState` — Tâche 4.8.
     */
    private fun openPublication(publicationId: String, targetLocator: Locator? = null) {
        viewModelScope.launch {
            val publication = publicationRepository.getById(publicationId) ?: run {
                Log.w("ReaderViewModel", "openPublication: publication introuvable ($publicationId)")
                return@launch
            }
            when (val result = publicationParser.parse(publication.fileUri)) {
                is ParseResult.Success -> {
                    currentPublicationId = publicationId
                    val restored = getReadingState(publicationId)
                    // Cascade de precedence (Blueprint §3.3, Tache 1.3) :
                    // surcharge de publication (ReadingState.overrides) >
                    // preferences globales. Resolue ici, jamais recalculee
                    // dans ReaderScreen (Tache 4.7).
                    val effectiveSettings = EffectiveReadingSettings.resolve(
                        overrides = restored?.overrides,
                        global = preferencesRepository.get(),
                    )
                    _state.value = ReaderUiState(
                        chapters = result.documentModel.chapters,
                        tableOfContents = result.documentModel.tableOfContents,
                        currentChapterIndex = restored?.locator?.chapterIndex ?: 0,
                        effectiveSettings = effectiveSettings,
                        currentOverrides = restored?.overrides,
                    )
                    triggerPreload(_state.value.currentChapterIndex)
                    observeAnnotations(publicationId)
                    observeBookmarks(publicationId)
                    // Tache 7.5 : arrivee depuis un resultat de recherche -
                    // appelee ICI (dans la meme coroutine, apres que
                    // _state.value.chapters soit peuple), pas via un second
                    // dispatch d'intent qui s'executerait avant que
                    // l'ouverture asynchrone soit terminee.
                    if (targetLocator != null) navigateToLocator(targetLocator)
                }
                else -> Log.w("ReaderViewModel", "openPublication: echec de parsing ($result)")
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
     * Construit l'`Annotation` à partir de la plage de phrases sélectionnée
     * (Tâche 7.1) — jamais d'offset arbitraire, l'index de `Sentence` est
     * connu par construction (sélection par phrase, voir
     * `AnnotationSelectionHandler`).
     */
    private fun confirmAnnotation(color: AnnotationColor) {
        val range = _state.value.selectedSentenceRange ?: return
        val chapter = _state.value.currentChapter ?: return
        val publicationId = currentPublicationId ?: return
        val sentences = chapter.paragraphs.flatMap { it.sentences }
        val (startLocator, endLocator) = annotationSelectionHandler.resolveSelection(
            sentences, range.first, range.last, chapter.index, chapter.href,
        ) ?: return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            addAnnotation(
                Annotation(
                    id = UUID.randomUUID().toString(),
                    publicationId = publicationId,
                    startLocator = startLocator,
                    endLocator = endLocator,
                    color = color,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            _state.value = _state.value.copy(selectionAnchorIndex = null, selectionFocusIndex = null)
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
     * Capture la position courante (Tâche 7.2) — plus simple que
     * [confirmAnnotation] : un seul `Locator`, pas de plage à résoudre.
     * Réutilise `Sentence.startLocator`, déjà utilisé par
     * [persistPosition]/[playCurrentSentence] pour la même conversion.
     */
    private fun createBookmarkAtCurrentPosition() {
        val chapter = _state.value.currentChapter ?: return
        val sentence = chapter.paragraphs.flatMap { it.sentences }.getOrNull(_state.value.currentSentenceIndex) ?: return
        val publicationId = currentPublicationId ?: return

        viewModelScope.launch {
            createBookmark(
                Bookmark(
                    id = UUID.randomUUID().toString(),
                    publicationId = publicationId,
                    locator = sentence.startLocator(chapterIndex = chapter.index, resourceHref = chapter.href),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Navigue vers un `Locator` de signet (Tâche 7.2) — change de chapitre
     * si besoin puis positionne sur la `Sentence` la plus proche de
     * `charOffset`, contrairement à `navigateToChapter` seul qui repositionne
     * toujours à l'index 0.
     */
    private fun navigateToLocator(locator: Locator) {
        val chapters = _state.value.chapters
        if (locator.chapterIndex !in chapters.indices) return
        val sentences = chapters[locator.chapterIndex].paragraphs.flatMap { it.sentences }
        val sentenceIndex = sentences.indexOfFirst { locator.charOffset in it.startOffset..it.endOffset }.coerceAtLeast(0)

        _state.value = _state.value.copy(
            currentChapterIndex = locator.chapterIndex, currentSentenceIndex = sentenceIndex,
            highlightedWordRange = null, isTocVisible = false, isBookmarkListVisible = false,
        )
        persistPosition(chapterIndex = locator.chapterIndex, sentenceIndex = sentenceIndex)
        triggerPreload(locator.chapterIndex)
    }

    private fun bootstrapAndOpenFixture(publicationId: String, fileUri: String) {
        viewModelScope.launch {
            // Idempotent depuis que PublicationDao.insert() n'est plus
            // OnConflictStrategy.REPLACE (Tache 7.1bis) : cette fixture a un
            // id et un fileHash fixes, appelee a chaque lancement debug -
            // sans cette verification, le deuxieme lancement sur un device
            // deja utilise levait SQLiteConstraintException (crash reel
            // observe en testant ce changement, pas suppose).
            if (publicationRepository.getById(publicationId) == null) {
                publicationRepository.insert(
                    Publication(
                        id = publicationId,
                        title = "Fixture marche a blanc",
                        format = PublicationFormat.EPUB,
                        fileUri = fileUri,
                        fileHash = "walking-skeleton-fixture-hash",
                        fileSize = java.io.File(fileUri).length(),
                        chapterCount = 1,
                        importDate = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private fun navigateToChapter(targetIndex: Int) {
        val chapters = _state.value.chapters
        if (targetIndex !in chapters.indices) return // pas de navigation hors bornes silencieuse
        _state.value = _state.value.copy(
            currentChapterIndex = targetIndex, currentSentenceIndex = 0,
            highlightedWordRange = null, isTocVisible = false,
        )
        persistPosition(chapterIndex = targetIndex, sentenceIndex = 0)
        triggerPreload(targetIndex)
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
            val chapter = _state.value.chapters.getOrNull(chapterIndex) ?: return@launch
            val sentence = chapter.paragraphs.flatMap { it.sentences }.getOrNull(sentenceIndex) ?: return@launch
            updateReadingState(
                ReadingState(
                    publicationId = currentPublicationId ?: return@launch,
                    locator = sentence.startLocator(chapterIndex = chapterIndex, resourceHref = chapter.href),
                    lastReadAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun playCurrentSentence() {
        val chapter = _state.value.currentChapter ?: return
        val sentence = chapter.paragraphs.flatMap { it.sentences }.getOrNull(_state.value.currentSentenceIndex) ?: return
        val publicationId = currentPublicationId ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(isPlaying = true)

            val voiceProfile = VoiceProfile(
                id = "vp-native-fr", engine = TtsEngineId.ANDROID_NATIVE,
                voice = "fr-fr-default", language = "fr-FR",
            )
            val segment = sentenceAudioBuffer.get(sentence, voiceProfile)
            audioSegmentPlayer.play(segment) // démarre en parallèle du surlignage ci-dessous — les deux dérivent leur timing du même événement de synthèse réel (Tâche 3.8)

            // Precharge la phrase suivante du meme chapitre pendant que
            // celle-ci se joue (Tache 5.3) - beneficie pleinement une fois
            // qu'une navigation phrase-a-phrase continue existe (Tache
            // 5.4/5.5) ; pour l'instant, prepare le terrain sans changer
            // le comportement observable (le Reader ne joue encore qu'une
            // phrase a la fois, Tache 4.5).
            chapter.paragraphs.flatMap { it.sentences }.getOrNull(_state.value.currentSentenceIndex + 1)?.let {
                sentenceAudioBuffer.preloadNext(it, voiceProfile)
            }

            segment.wordTimestamps.forEach { wt ->
                _state.value = _state.value.copy(
                    highlightedWordRange = wt.charOffset until (wt.charOffset + wt.word.length),
                )
                delay((wt.endMs - wt.startMs).coerceAtLeast(0L))
            }

            _state.value = _state.value.copy(isPlaying = false, highlightedWordRange = null)

            updateReadingState(
                ReadingState(
                    publicationId = publicationId,
                    locator = sentence.startLocator(chapterIndex = chapter.index, resourceHref = chapter.href),
                    lastReadAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
