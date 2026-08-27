package com.inktone.domain.usecase

import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.PreAnalysisStore
import com.inktone.domain.service.TtsSegmentCache

/**
 * Lot 2b.2 — retrait irréversible d'une publication (UX §Bibliothèque
 * état peuplé, popup d'actions par livre). La cascade vers
 * BookmarkEntity/AnnotationEntity/ReadingStateEntity/ReadingSessionEntity
 * est garantie par le schéma (`onDelete = ForeignKey.CASCADE`), pas par
 * de la logique ici — voir `CascadeDeleteTest`.
 *
 * Lot 22 — la pré-analyse persistée et le cache TTS vivent hors Room
 * (fichiers par publication) et ne bénéficient donc pas du `CASCADE` :
 * leur purge est explicite ici (décision 1/3), jamais implicite.
 */
class DeletePublicationUseCase(
    private val publicationRepository: PublicationRepository,
    private val preAnalysisStore: PreAnalysisStore,
    private val ttsSegmentCache: TtsSegmentCache,
) {
    suspend operator fun invoke(publicationId: String) {
        publicationRepository.delete(publicationId)
        preAnalysisStore.delete(publicationId)
        ttsSegmentCache.deletePublication(publicationId)
    }
}
