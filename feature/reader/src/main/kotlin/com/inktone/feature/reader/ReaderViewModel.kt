package com.inktone.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Squelette MVI de la marche à blanc — une seule phrase, pas de
 * navigation de chapitre complète (Phase 4). L'audio est joué via
 * MediaPlayer directement ici ; AudioPlaybackService (Phase 5) le
 * remplacera pour la lecture en arrière-plan.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val ttsEngine: TtsEngine, // injecte AndroidNativeTtsEngine (Palier 1) via Hilt (infrastructure/tts/di/TtsModule)
    private val updateReadingState: UpdateReadingStateUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var currentSentence: Sentence? = null

    fun onIntent(intent: ReaderIntent) {
        when (intent) {
            is ReaderIntent.LoadSentence -> {
                currentSentence = Sentence(index = 0, text = intent.text, startOffset = 0, endOffset = intent.text.length)
                _state.value = _state.value.copy(sentenceText = intent.text, highlightedWordRange = null)
            }
            is ReaderIntent.PlayCurrentSentence -> playCurrentSentence()
            is ReaderIntent.Pause -> _state.value = _state.value.copy(isPlaying = false)
        }
    }

    private fun playCurrentSentence() {
        val sentence = currentSentence ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isPlaying = true)

            val voiceProfile = VoiceProfile(
                id = "vp-native-fr", engine = TtsEngineId.ANDROID_NATIVE,
                voice = "fr-fr-default", language = "fr-FR",
            )
            val segment = ttsEngine.synthesize(sentence, voiceProfile)

            // Lecture simplifiée pour la marche à blanc : on rejoue les
            // WordTimestamp via un minuteur plutôt que de lire l'audio en
            // synchronisation stricte — suffisant pour valider la chaîne
            // Locator -> surlignage -> reprise. La synchronisation audio
            // réelle (MediaPlayer/AudioTrack sur segment.audioData) est un
            // point à compléter avant de considérer cette tâche terminée,
            // volontairement non détaillé ici pour ne pas dupliquer le
            // travail de AudioPlaybackService prévu en Phase 5.
            segment.wordTimestamps.forEach { wt ->
                _state.value = _state.value.copy(
                    highlightedWordRange = wt.charOffset until (wt.charOffset + wt.word.length),
                )
                delay((wt.endMs - wt.startMs).coerceAtLeast(0L))
            }

            _state.value = _state.value.copy(isPlaying = false, highlightedWordRange = null)

            // K3 : persistance après la lecture de la phrase — un seul
            // chemin d'écriture pour cette marche à blanc (le scroll
            // manuel silencieux, deuxième chemin K3, est hors de portée
            // ici : une seule phrase, pas de scroll — Phase 4 le couvrira).
            updateReadingState(
                ReadingState(
                    publicationId = "walking-skeleton-fixture",
                    locator = sentence.startLocator(chapterIndex = 0, resourceHref = "OEBPS/chapter1.xhtml"),
                    lastReadAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
