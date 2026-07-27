package com.inktone.feature.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationParser
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var currentPublicationId: String? = null

    fun onIntent(intent: ReaderIntent) {
        when (intent) {
            is ReaderIntent.OpenPublication -> openPublication(intent.publicationId)
            is ReaderIntent.BootstrapAndOpenFixture -> bootstrapAndOpenFixture(intent.publicationId, intent.fileUri)
            is ReaderIntent.NextChapter -> navigateToChapter(_state.value.currentChapterIndex + 1)
            is ReaderIntent.PreviousChapter -> navigateToChapter(_state.value.currentChapterIndex - 1)
            is ReaderIntent.JumpToChapter -> navigateToChapter(intent.chapterIndex)
            is ReaderIntent.ToggleToc -> _state.value = _state.value.copy(isTocVisible = !_state.value.isTocVisible)
            is ReaderIntent.PlayCurrentSentence -> playCurrentSentence()
            is ReaderIntent.Pause -> _state.value = _state.value.copy(isPlaying = false)
        }
    }

    /**
     * Ouvre une publication déjà importée : récupère son `fileUri` via
     * le repository, parse le contenu (CompositePublicationParser),
     * puis restaure la dernière position connue (K3) si elle existe.
     * Les cas d'erreur de parsing (Corrompu, DRM, format non supporté)
     * ne sont pas encore reflétés dans `ReaderUiState` — Tâche 4.8.
     */
    private fun openPublication(publicationId: String) {
        viewModelScope.launch {
            val publication = publicationRepository.getById(publicationId) ?: run {
                Log.w("ReaderViewModel", "openPublication: publication introuvable ($publicationId)")
                return@launch
            }
            when (val result = publicationParser.parse(publication.fileUri)) {
                is ParseResult.Success -> {
                    currentPublicationId = publicationId
                    val restored = getReadingState(publicationId)
                    _state.value = ReaderUiState(
                        chapters = result.documentModel.chapters,
                        tableOfContents = result.documentModel.tableOfContents,
                        currentChapterIndex = restored?.locator?.chapterIndex ?: 0,
                    )
                }
                else -> Log.w("ReaderViewModel", "openPublication: echec de parsing ($result)")
            }
        }
    }

    private fun bootstrapAndOpenFixture(publicationId: String, fileUri: String) {
        viewModelScope.launch {
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
            openPublication(publicationId)
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
            val segment = ttsEngine.synthesize(sentence, voiceProfile)
            audioSegmentPlayer.play(segment) // démarre en parallèle du surlignage ci-dessous — les deux dérivent leur timing du même événement de synthèse réel (Tâche 3.8)

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
