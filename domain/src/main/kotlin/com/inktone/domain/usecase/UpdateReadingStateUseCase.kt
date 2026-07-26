package com.inktone.domain.usecase

import com.inktone.domain.model.ReadingState
import com.inktone.domain.repository.ReadingStateRepository

/**
 * Persiste la position de lecture (acquis K3). Rappel Blueprint §7.7 :
 * cette fonction est appelée par DEUX chemins distincts (transition de
 * phrase TTS, scroll manuel debouncé) qui ne s'exécutent JAMAIS
 * simultanément. Cette classe ne connaît pas l'appelant — la garantie
 * d'exclusivité est de la responsabilité du ViewModel Reader (Phase 3/4),
 * pas de ce Use Case.
 */
class UpdateReadingStateUseCase(
    private val readingStateRepository: ReadingStateRepository,
) {
    suspend operator fun invoke(state: ReadingState) {
        readingStateRepository.save(state)
    }
}
