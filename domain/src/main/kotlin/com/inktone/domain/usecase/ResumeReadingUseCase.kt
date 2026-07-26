package com.inktone.domain.usecase

import com.inktone.domain.model.ReadingState
import com.inktone.domain.repository.ReadingStateRepository

/**
 * Reprend la lecture d'une publication au [ReadingState] persisté —
 * jamais au début du chapitre (acquis K3).
 *
 * SIGNATURE UNIQUEMENT en Phase 1 — le corps réel exige l'état Reader et
 * l'orchestration TTS (Phase 3/4 : ViewModel Reader, infrastructure/tts).
 * Ne pas invoquer avant l'injection d'implémentations réelles.
 *
 * Contrat :
 * - Entrée : identifiant de la publication à reprendre.
 * - Sortie : un [ResumeResult] typé — jamais d'exception pour un cas
 *   métier attendu (Blueprint §7.11).
 */
class ResumeReadingUseCase(
    private val readingStateRepository: ReadingStateRepository,
) {
    suspend operator fun invoke(publicationId: String): ResumeResult {
        TODO("Complété en Phase 3/4 — nécessite l'état Reader et l'orchestration TTS")
    }
}

sealed interface ResumeResult {
    data class Success(val readingState: ReadingState) : ResumeResult
    data object NoPreviousState : ResumeResult
}
