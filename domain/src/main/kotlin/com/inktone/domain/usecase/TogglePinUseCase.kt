package com.inktone.domain.usecase

import com.inktone.domain.repository.PublicationRepository

/** Lot 2b.1 — remonte une publication en tête de la bibliothèque (UX §Bibliothèque état peuplé). */
class TogglePinUseCase(
    private val publicationRepository: PublicationRepository,
) {
    suspend operator fun invoke(publicationId: String, isPinned: Boolean) {
        publicationRepository.setPinned(publicationId, isPinned)
    }
}
