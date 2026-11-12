package com.inktone.domain.usecase

import com.inktone.domain.model.progressionAt
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingSessionRepository
import com.inktone.domain.repository.ReadingStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * UseCase « livre en cours » (Lot Statistiques Palier 2).
 *
 * Retourne un [Flow] pour intégration directe dans `combine()` —
 * pas de `flow { emit() }` wrapper dans le ViewModel. Exécuté
 * sur [Dispatchers.Default] pour ne jamais bloquer le thread UI.
 */
class GetCurrentBookUseCase(
    private val readingSessionRepository: ReadingSessionRepository,
    private val publicationRepository: PublicationRepository,
    private val readingStateRepository: ReadingStateRepository,
) {
    operator fun invoke(): Flow<CurrentBookState?> = flow {
        val publicationId = readingSessionRepository.getLastReadPublicationId()
        if (publicationId == null) {
            emit(null)
            return@flow
        }
        val publication = publicationRepository.getById(publicationId)
        if (publication == null) {
            emit(null)
            return@flow
        }
        val readingState = readingStateRepository.get(publicationId)

        // Même calcul que le Lecteur et la Bibliothèque — plus l'ancien
        // `(chapterIndex + 1) / chapterCount`, qui comptait un chapitre
        // entamé comme lu (100 % dès l'ouverture d'un livre à un chapitre).
        val progressPercent = when (readingState) {
            null -> 0f
            else -> publication.progressionAt(readingState.locator)
        }

        val sessions = readingSessionRepository.getByPublicationId(publicationId)
        val totalBookTimeMs = sessions.sumOf { it.durationMs }

        emit(
            CurrentBookState(
                id = publication.id,
                title = publication.title,
                coverUri = publication.coverUri,
                progressPercent = progressPercent,
                totalBookTimeMs = totalBookTimeMs,
                remainingTimeFormatted = null,
            )
        )
    }.flowOn(Dispatchers.Default)
}
