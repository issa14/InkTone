package com.inktone.domain.usecase

import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.PreAnalysisStore

/**
 * Lot 2b.2 — retrait irréversible d'une publication (UX §Bibliothèque
 * état peuplé, popup d'actions par livre). La cascade vers
 * BookmarkEntity/AnnotationEntity/ReadingStateEntity/ReadingSessionEntity
 * est garantie par le schéma (`onDelete = ForeignKey.CASCADE`), pas par
 * de la logique ici — voir `CascadeDeleteTest`.
 *
 * Lot 22, Palier A — la pré-analyse persistée vit hors Room (fichier par
 * publication) et ne bénéficie donc pas du `CASCADE` : sa purge est
 * explicite ici (décision 1), jamais implicite.
 */
class DeletePublicationUseCase(
    private val publicationRepository: PublicationRepository,
    private val preAnalysisStore: PreAnalysisStore,
) {
    suspend operator fun invoke(publicationId: String) {
        publicationRepository.delete(publicationId)
        preAnalysisStore.delete(publicationId)
    }
}
