package com.inktone.domain.usecase

import com.inktone.domain.repository.PublicationRepository

/**
 * Exporte la bibliothèque (ou une sélection) vers une destination choisie
 * par l'utilisateur via SAF.
 *
 * SIGNATURE UNIQUEMENT en Phase 1 — le corps réel exige un accès SAF en
 * écriture (infrastructure/storage, complété en Phase 6). L'interface de
 * domaine pour cet accès n'existe pas encore : elle sera ajoutée avec ce
 * Use Case en Phase 6, pas inventée par anticipation ici. Ne pas invoquer
 * avant cette phase.
 */
class ExportLibraryUseCase(
    private val publicationRepository: PublicationRepository,
) {
    suspend operator fun invoke(destinationUri: String): ExportResult {
        TODO("Complété en Phase 6 — nécessite un accès SAF en écriture (infrastructure/storage)")
    }
}

sealed interface ExportResult {
    data class Success(val exportedCount: Int) : ExportResult
    data class Failure(val message: String) : ExportResult
}
