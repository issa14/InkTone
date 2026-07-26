package com.inktone.domain.usecase

import com.inktone.domain.model.Publication
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.PublicationParser

/**
 * Importe une publication depuis une URI SAF.
 *
 * SIGNATURE UNIQUEMENT en Phase 1 — le corps réel (extraction de
 * métadonnées, détection de doublons par [Publication.fileHash],
 * détection DRM K7) exige [PublicationParser] (infrastructure/parser,
 * complété en Phase 4) et l'accès fichier SAF (infrastructure/storage,
 * Phase 2). Ne pas invoquer avant l'injection d'implémentations réelles.
 *
 * Contrat :
 * - Entrée : URI SAF d'un fichier sélectionné par l'utilisateur.
 * - Sortie : un [ImportResult] typé — jamais d'exception pour un cas
 *   métier attendu (Blueprint §7.11).
 */
class ImportPublicationUseCase(
    private val publicationParser: PublicationParser,
    private val publicationRepository: PublicationRepository,
) {
    suspend operator fun invoke(fileUri: String): ImportResult {
        TODO("Complété en Phase 4/6 — nécessite PublicationParser et la détection de doublons par hash (K2, K7)")
    }
}

sealed interface ImportResult {
    data class Success(val publication: Publication) : ImportResult
    data class Duplicate(val existingPublicationId: String) : ImportResult
    data class DrmProtected(val message: String) : ImportResult
    data class Corrupted(val message: String) : ImportResult
    data class UnsupportedFormat(val format: String) : ImportResult
}
