package com.inktone.domain.usecase

import com.inktone.domain.model.PositionConflict
import com.inktone.domain.model.ReadingState
import com.inktone.domain.repository.ConflictQueueRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.valueobject.Locator

/**
 * Applique le choix de l'utilisateur pour un [PositionConflict] (tâche
 * 11.10) : écrit la position choisie comme nouvelle [com.inktone.domain
 * .model.ReadingState], retire le conflit de la file. Le choix n'est
 * jamais présélectionné côté UI — la plus récente n'est pas
 * nécessairement celle que l'utilisateur veut garder.
 *
 * Pas de `@Inject` (domain reste pur Kotlin, Tâche 1.8) — fourni par
 * `UseCaseModule` (`data/di`).
 */
class ResolvePositionConflictUseCase(
    private val readingStateRepository: ReadingStateRepository,
    private val conflictQueueRepository: ConflictQueueRepository,
) {
    suspend operator fun invoke(conflict: PositionConflict, chosenLocator: Locator) {
        val current = readingStateRepository.get(conflict.publicationId)
        val resolved = (current ?: ReadingState(
            publicationId = conflict.publicationId,
            locator = chosenLocator,
            lastReadAt = System.currentTimeMillis(),
        )).copy(locator = chosenLocator, lastReadAt = System.currentTimeMillis())
        readingStateRepository.save(resolved)
        conflictQueueRepository.remove(conflict.publicationId)
    }
}
