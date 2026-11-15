package com.inktone.domain.usecase

import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.FileStorageService
import kotlinx.coroutines.flow.first

/**
 * Rattrapage des livres importés avant ADR-028 : copie dans le stockage de
 * l'application chaque livre qui ne pointe encore que sur le fichier choisi
 * par l'utilisateur, puis fait pointer le livre sur sa copie.
 *
 * Ne réussit que tant que la permission sur le fichier d'origine tient. Un
 * échec laisse le livre tel quel : le lancement suivant réessaiera.
 */
class CopyLibraryToPrivateStorageUseCase(
    private val publicationRepository: PublicationRepository,
    private val fileStorageService: FileStorageService,
) {
    data class Outcome(val copied: Int, val failed: Int)

    suspend operator fun invoke(): Outcome {
        var copied = 0
        var failed = 0
        for (publication in publicationRepository.observeAll().first()) {
            if (fileStorageService.isAppOwned(publication.fileUri)) continue
            // L'extension, que les parseurs lisent pour choisir le format,
            // vient du format enregistré : le nom d'origine peut ne plus être lisible.
            val copy = fileStorageService.copyToLibrary(
                uri = publication.fileUri,
                fileName = "livre.${publication.format.extension()}",
                fileHash = publication.fileHash,
            )
            if (copy == null) {
                failed++
            } else {
                publicationRepository.setFileUri(publication.id, copy)
                copied++
            }
        }
        return Outcome(copied, failed)
    }

    private fun PublicationFormat.extension(): String = when (this) {
        PublicationFormat.EPUB -> "epub"
        PublicationFormat.PDF -> "pdf"
        PublicationFormat.TXT -> "txt"
    }
}
