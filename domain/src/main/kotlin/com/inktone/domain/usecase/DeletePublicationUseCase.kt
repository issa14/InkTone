package com.inktone.domain.usecase

import com.inktone.domain.repository.PublicationRepository

/**
 * Lot 2b.2 — retrait irréversible d'une publication (UX §Bibliothèque
 * état peuplé, popup d'actions par livre). La cascade vers
 * BookmarkEntity/AnnotationEntity/ReadingStateEntity/ReadingSessionEntity
 * est garantie par le schéma (`onDelete = ForeignKey.CASCADE`), pas par
 * de la logique ici — voir `CascadeDeleteTest`.
 */
class DeletePublicationUseCase(
    private val publicationRepository: PublicationRepository,
) {
    suspend operator fun invoke(publicationId: String) {
        publicationRepository.delete(publicationId)
    }
}
