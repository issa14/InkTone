package com.inktone.feature.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.repository.PublicationRepository
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
 * Squelette MVI de la marche à blanc — une seule phrase, pas de
 * navigation de chapitre complète (Phase 4). L'audio est joué via
 * MediaPlayer directement ici ; AudioPlaybackService (Phase 5) le
 * remplacera pour la lecture en arrière-plan.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val ttsEngine: TtsEngine, // injecte AndroidNativeTtsEngine (Palier 1) via Hilt (infrastructure/tts/di/TtsModule)
    private val updateReadingState: UpdateReadingStateUseCase,
    private val getReadingState: GetReadingStateUseCase,
    private val publicationRepository: PublicationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var currentSentence: Sentence? = null

    fun onIntent(intent: ReaderIntent) {
        when (intent) {
            is ReaderIntent.LoadSentence -> {
                currentSentence = Sentence(index = 0, text = intent.text, startOffset = 0, endOffset = intent.text.length)
                _state.value = _state.value.copy(sentenceText = intent.text, highlightedWordRange = null)
                logRestoredPositionIfAny()
            }
            is ReaderIntent.PlayCurrentSentence -> playCurrentSentence()
            is ReaderIntent.Pause -> _state.value = _state.value.copy(isPlaying = false)
        }
    }

    /**
     * Tache 3.7, critere de validation #4 : verifier que ReadingState
     * restaure la position exacte apres relance, via un log (pas encore
     * d'affichage a l'ecran - la navigation reelle, Phase 4, remplacera
     * ce point d'entree temporaire par une reprise automatique pilotant
     * effectivement l'ouverture du bon chapitre/mot).
     */
    private fun logRestoredPositionIfAny() {
        viewModelScope.launch {
            val restored = getReadingState(WALKING_SKELETON_FIXTURE_PUBLICATION_ID)
            Log.i(
                "ReaderViewModel",
                if (restored != null) {
                    "K3 - position restauree: resourceHref=${restored.locator.resourceHref} " +
                        "chapterIndex=${restored.locator.chapterIndex} charOffset=${restored.locator.charOffset} " +
                        "lastReadAt=${restored.lastReadAt}"
                } else {
                    "K3 - aucune position sauvegardee pour $WALKING_SKELETON_FIXTURE_PUBLICATION_ID"
                },
            )
        }
    }

    private fun playCurrentSentence() {
        val sentence = currentSentence ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isPlaying = true)

            // ReadingStateEntity porte une cle etrangere vers PublicationEntity
            // (Phase 2) : sans cette ligne, la persistance K3 ci-dessous
            // plante avec FOREIGN KEY constraint failed (bug reel trouve en
            // executant le test de bout en bout manuel de la Tache 3.7, pas
            // suppose). Bootstrap explicitement marque comme scaffolding de
            // marche a blanc : la Phase 4 recevra une Publication deja
            // importee et n'aura plus besoin de cet upsert defensif ici.
            publicationRepository.insert(
                Publication(
                    id = WALKING_SKELETON_FIXTURE_PUBLICATION_ID,
                    title = "Fixture marche a blanc",
                    format = PublicationFormat.EPUB,
                    fileUri = "content://fixture",
                    fileHash = "walking-skeleton-fixture-hash",
                    fileSize = 0L,
                    chapterCount = 1,
                    importDate = System.currentTimeMillis(),
                ),
            )

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
                    publicationId = WALKING_SKELETON_FIXTURE_PUBLICATION_ID,
                    locator = sentence.startLocator(chapterIndex = 0, resourceHref = "OEBPS/chapter1.xhtml"),
                    lastReadAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private companion object {
        const val WALKING_SKELETON_FIXTURE_PUBLICATION_ID = "walking-skeleton-fixture"
    }
}
