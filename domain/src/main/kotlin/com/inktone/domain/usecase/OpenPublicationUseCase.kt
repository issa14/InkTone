package com.inktone.domain.usecase

import com.inktone.domain.model.DocumentModel
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.PublicationParser

/**
 * Ouvre une publication déjà importée et produit son [DocumentModel]
 * pour le Reader.
 *
 * SIGNATURE UNIQUEMENT en Phase 1 — le corps réel exige [PublicationParser]
 * (infrastructure/parser, complété en Phase 4). Ne pas invoquer avant
 * l'injection d'une implémentation réelle.
 *
 * Contrat :
 * - Entrée : identifiant d'une [com.inktone.domain.model.Publication]
 *   déjà présente dans la bibliothèque.
 * - Sortie : un [OpenResult] typé — jamais d'exception pour un cas métier
 *   attendu (Blueprint §7.11).
 */
class OpenPublicationUseCase(
    private val publicationRepository: PublicationRepository,
    private val publicationParser: PublicationParser,
) {
    suspend operator fun invoke(publicationId: String): OpenResult {
        TODO("Complété en Phase 4 — nécessite PublicationParser")
    }
}

sealed interface OpenResult {
    data class Success(val documentModel: DocumentModel) : OpenResult
    data object NotFound : OpenResult
    data class Corrupted(val message: String) : OpenResult
}
