package com.inktone.domain.usecase

import com.inktone.domain.model.Publication
import com.inktone.domain.model.cleanedForDisplay
import com.inktone.domain.model.progressionAt
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingSessionRepository
import com.inktone.domain.repository.ReadingStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn

/**
 * UseCase « livre en cours » des Statistiques : le livre de reprise, même règle
 * que la carte « Reprendre la lecture » de la Bibliothèque ([resumePublication],
 * audit UX D33). Observé : il suit l'ouverture d'un autre livre.
 *
 * Progression `null` (inconnue) sans état de lecture ou sans longueurs de
 * chapitres, jamais un 0 % inventé.
 */
class GetCurrentBookUseCase(
    private val readingSessionRepository: ReadingSessionRepository,
    private val publicationRepository: PublicationRepository,
    private val readingStateRepository: ReadingStateRepository,
) {
    operator fun invoke(): Flow<CurrentBookState?> =
        publicationRepository.observeAll()
            .map { publications -> publications.resumePublication() }
            .map { publication -> publication?.let { toState(it) } }
            .flowOn(Dispatchers.Default)

    private suspend fun toState(publication: Publication): CurrentBookState {
        val readingState = readingStateRepository.get(publication.id)
        val totalBookTimeMs = readingSessionRepository.getByPublicationId(publication.id).sumOf { it.durationMs }
        return CurrentBookState(
            id = publication.id,
            title = publication.title.cleanedForDisplay(),
            coverUri = publication.coverUri,
            progressPercent = readingState?.let { publication.progressionAt(it.locator) },
            totalBookTimeMs = totalBookTimeMs,
            totalChars = publication.chapterLengths?.total,
            remainingTimeFormatted = null,
        )
    }
}
