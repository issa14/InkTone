package com.inktone.domain.usecase

import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.FileStorageService
import com.inktone.domain.service.PreAnalysisStore
import com.inktone.domain.service.RenderedPageCache
import com.inktone.domain.service.TtsSegmentCache
import com.inktone.domain.service.PlaybackSession
import com.inktone.domain.service.PlaybackSessionState
import com.inktone.domain.service.SearchService
import kotlinx.coroutines.flow.first

/**
 * Lot 2b.2 — retrait irréversible d'une publication (UX §Bibliothèque
 * état peuplé, popup d'actions par livre). La cascade vers
 * BookmarkEntity/AnnotationEntity/ReadingStateEntity/ReadingSessionEntity
 * est garantie par le schéma (`onDelete = ForeignKey.CASCADE`), pas par
 * de la logique ici — voir `CascadeDeleteTest`.
 *
 * Lot 22 — la pré-analyse persistée, le cache TTS et le cache de pages PDF
 * vivent hors Room (fichiers par publication) et ne bénéficient donc pas
 * du `CASCADE` : leur purge est explicite ici (décision 1/3), jamais
 * implicite.
 */
class DeletePublicationUseCase(
    private val publicationRepository: PublicationRepository,
    private val preAnalysisStore: PreAnalysisStore,
    private val ttsSegmentCache: TtsSegmentCache,
    private val renderedPageCache: RenderedPageCache,
    private val playbackSession: PlaybackSession,
    private val searchService: SearchService,
    private val fileStorageService: FileStorageService,
) {
    suspend operator fun invoke(publicationId: String) {
        // Audit UX A12 : supprimer le livre en cours de narration laissait la
        // voix lire et le mini-lecteur afficher un livre retiré, et
        // l'ordonnanceur tentait d'écrire une position pour un livre absent.
        if (playbackSession.sessionState.value != PlaybackSessionState.IDLE &&
            playbackSession.metadata.value.publicationId == publicationId
        ) {
            playbackSession.stop()
        }
        val publication = publicationRepository.getById(publicationId)
        val fileUri = publication?.fileUri
        // Le nom d'une couverture dérive d'un hachage 32 bits de l'URI : deux livres
        // peuvent, rarement, en partager le fichier, qu'on ne supprime alors pas.
        val coverUri = publication?.coverUri?.takeIf { cover ->
            publicationRepository.observeAll().first().none { it.id != publicationId && it.coverUri == cover }
        }
        publicationRepository.delete(publicationId)
        coverUri?.let { fileStorageService.deleteOwnedFile(it) }
        // Copie privée ou téléchargement OPDS : le fichier appartient au livre
        // (ADR-028, audit UX D63). Le fichier d'origine d'un ancien import
        // reste à l'utilisateur.
        fileUri?.let { fileStorageService.deleteOwnedFile(it) }
        preAnalysisStore.delete(publicationId)
        ttsSegmentCache.deletePublication(publicationId)
        renderedPageCache.deletePublication(publicationId)
        // Audit UX D10 : les phrases d'un livre retiré restaient dans l'index
        // de recherche (seule la requête les écartait).
        searchService.deletePublication(publicationId)
    }
}
