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
import com.inktone.domain.repository.VoiceProfileRepository
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
    private val voiceProfileRepository: VoiceProfileRepository,
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

    // C.5 — exposé pour clé sharedElement dans ReaderScreen
    internal var currentPublicationId: String? = null
    private val chapterPreloader = ChapterPreloader(viewModelScope)
    private val sentenceAudioBuffer = SentenceAudioBuffer(viewModelScope, ttsEngine)
    private val annotationSelectionHandler = AnnotationSelectionHandler()
    private var sleepTimerJob: Job? = null

    // A.1bis — job de la coroutine de lecture TTS en cours (une phrase, ou
    // la chaîne auto-avance). Annulé par pausePlayback()/skipSentence() en
    // plus de audioSegmentPlayer.stop() : sans ça, la boucle de surlignage
    // mot-à-mot de playCurrentSentence() continuait d'avancer silencieusement
    // après une pause, seul le son s'arrêtait (bug réel trouvé à l'audit).
    private var playbackJob: Job? = null

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
            is ReaderIntent.BootstrapAndOpenFixture -> {
                if (BuildConfig.DEBUG) {
                    bootstrapAndOpenFixture(intent.publicationId, intent.fileUri)
                }
            }
            is ReaderIntent.NextChapter -> navigateToChapter(_state.value.currentChapterIndex + 1)
            is ReaderIntent.PreviousChapter -> navigateToChapter(_state.value.currentChapterIndex - 1)
            is ReaderIntent.JumpToChapter -> navigateToChapter(intent.chapterIndex)
            is ReaderIntent.ToggleToc -> _state.value = _state.value.copy(isTocVisible = !_state.value.isTocVisible)
            is ReaderIntent.PlayCurrentSentence -> playCurrentSentence()
            is ReaderIntent.Pause -> pausePlayback()
            is ReaderIntent.DismissError -> _state.value = _state.value.copy(errorMessage = null)
            is ReaderIntent.ToggleReadingMode -> {
                val newMode = if (_state.value.readingMode == ReadingMode.SCROLL) ReadingMode.PAGED else ReadingMode.SCROLL
                _state.value = _state.value.copy(readingMode = newMode)
                // B.1 — persiste le mode de lecture
                viewModelScope.launch {
                    val current = preferencesRepository.get()
                    preferencesRepository.update(current.copy(readingMode = newMode.name))
                }
            }
            is ReaderIntent.BeginSentenceSelection -> _state.value = _state.value.copy(
                selectionAnchorIndex = intent.sentenceIndex, selectionFocusIndex = intent.sentenceIndex,
            )
            is ReaderIntent.ExtendSentenceSelection -> _state.value = _state.value.copy(selectionFocusIndex = intent.sentenceIndex)
            is ReaderIntent.ClearSentenceSelection -> _state.value = _state.value.copy(
                selectionAnchorIndex = null, selectionFocusIndex = null,
            )
            is ReaderIntent.ConfirmAnnotation -> confirmAnnotation(intent.color, intent.content)
            is ReaderIntent.ToggleBookmarkAtCurrentPosition -> toggleBookmarkAtCurrentPosition()
            is ReaderIntent.ToggleBookmarkList -> _state.value = _state.value.copy(
                isBookmarkListVisible = !_state.value.isBookmarkListVisible,
            )
            is ReaderIntent.DeleteBookmark -> viewModelScope.launch { deleteBookmark(intent.id) }
            is ReaderIntent.NavigateToLocator -> navigateToLocator(intent.locator)
            is ReaderIntent.SetOverrides -> setOverrides(intent.overrides)
            is ReaderIntent.SetSleepTimer -> setSleepTimer(intent.minutes)
            is ReaderIntent.SkipToPreviousSentence -> skipSentence(-1)
            is ReaderIntent.SkipToNextSentence -> skipSentence(1)
            is ReaderIntent.UpdateScrollPosition -> updateScrollPosition(intent.sentenceIndex)
        }
    }

    private var scrollPersistJob: Job? = null

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
                _state.value = _state.value.copy(errorMessage = "Publication introuvable.")
                return@launch
            }
            when (val result = publicationParser.parse(publication.fileUri)) {
                is ParseResult.Success -> {
                    currentPublicationId = publicationId
                    val restored = getReadingState(publicationId)
                    val effectiveSettings = EffectiveReadingSettings.resolve(
                        overrides = restored?.overrides,
                        global = preferencesRepository.get(),
                    )
                    val prefs = preferencesRepository.get()
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
                        // B.1 — restaure le mode de lecture persisté
                        readingMode = if (prefs.readingMode == "PAGED") ReadingMode.PAGED else ReadingMode.SCROLL,
                        currentOverrides = restored?.overrides,
                    )
                    triggerPreload(_state.value.currentChapterIndex)
                    observeAnnotations(publicationId)
                    observeBookmarks(publicationId)
                    if (targetLocator != null) navigateToLocator(targetLocator)
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
     * Construit l'`Annotation` à partir de la plage de phrases sélectionnée
     * (Tâche 7.1) — jamais d'offset arbitraire, l'index de `Sentence` est
     * connu par construction (sélection par phrase, voir
     * `AnnotationSelectionHandler`).
     */
    private fun confirmAnnotation(color: AnnotationColor, content: String? = null) {
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
                    content = content,
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
     * Tâche 3c.3 — toggle « Marquer cette page » : retire le signet déjà
     * présent à la position courante s'il existe (jamais de doublon,
     * cible confirmée dans `UX_FLOW_DESIGN.md`), sinon en crée un. Même
     * conversion `Sentence.startLocator` que [persistPosition]/
     * [playCurrentSentence] — une seule source pour « la position
     * courante », jamais un second calcul.
     */
    private fun toggleBookmarkAtCurrentPosition() {
        val chapter = _state.value.currentChapter ?: return
        val sentence = chapter.paragraphs.flatMap { it.sentences }.getOrNull(_state.value.currentSentenceIndex) ?: return
        val publicationId = currentPublicationId ?: return
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
    private fun navigateToLocator(locator: Locator) {
        val chapters = _state.value.chapters
        if (locator.chapterIndex !in chapters.indices) return
        val sentences = chapters[locator.chapterIndex].paragraphs.flatMap { it.sentences }
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
        triggerPreload(targetIndex)
        if (wasPlaying) playCurrentSentence()
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
        playbackJob = viewModelScope.launch {
            val chapter = _state.value.currentChapter ?: return@launch
            val sentences = chapter.paragraphs.flatMap { it.sentences }
            val index = _state.value.currentSentenceIndex
            val publicationId = currentPublicationId ?: return@launch

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
                    _state.value = _state.value.copy(isPlaying = false)
                }
                return@launch
            }

            _state.value = _state.value.copy(isPlaying = true)

            val sentence = sentences[index]
            // A.5 — résout le profil vocal actif depuis les préférences utilisateur
            val prefs = preferencesRepository.get()
            val voiceProfile = prefs.activeVoiceProfileId
                ?.let { voiceProfileRepository.getById(it) }
                ?: VoiceProfile(
                    id = "vp-native-fr", engine = TtsEngineId.ANDROID_NATIVE,
                    voice = "fr-fr-default", language = "fr-FR",
                )
            val segment = sentenceAudioBuffer.get(sentence, voiceProfile)
            audioSegmentPlayer.play(segment)

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

            _state.value = _state.value.copy(highlightedWordRange = null)

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
        _state.value = _state.value.copy(isPlaying = false, highlightedWordRange = null)
    }

    /**
     * Panneau TTS (Tâche B.3) — recule/avance d'une phrase dans le
     * chapitre courant. Reprend immédiatement la lecture sur la nouvelle
     * phrase si elle était déjà en cours ; sinon se contente de déplacer
     * la position (mêmes règles K3 que la navigation manuelle).
     */
    private fun skipSentence(delta: Int) {
        val chapter = _state.value.currentChapter ?: return
        val sentences = chapter.paragraphs.flatMap { it.sentences }
        if (sentences.isEmpty()) return
        val wasPlaying = _state.value.isPlaying
        pausePlayback()
        val newIndex = (_state.value.currentSentenceIndex + delta).coerceIn(0, sentences.lastIndex)
        _state.value = _state.value.copy(currentSentenceIndex = newIndex)
        persistPosition(chapterIndex = chapter.index, sentenceIndex = newIndex)
        if (wasPlaying) playCurrentSentence()
    }

    /**
     * A.2 — Nettoyage des ressources audio et du minuteur de sommeil
     * quand le ViewModel est détruit. Évite qu'un segment audio continue
     * de jouer après la destruction de l'écran.
     */
    override fun onCleared() {
        super.onCleared()
        audioSegmentPlayer.stop()
        sleepTimerJob?.cancel()
        scrollPersistJob?.cancel()
    }
}

/** Tâche 3c.1 — au changement de phrase visible, pas à chaque pixel défilé. */
private const val SCROLL_PERSIST_DEBOUNCE_MS = 400L
