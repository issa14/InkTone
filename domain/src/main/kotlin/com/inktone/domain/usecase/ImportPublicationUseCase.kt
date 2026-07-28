package com.inktone.domain.usecase

import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.FileStorageService
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationMetadata
import com.inktone.domain.service.PublicationParser
import java.util.UUID

/**
 * Importe une publication depuis une URI SAF.
 *
 * Orchestration pure (Tâche 6.1) — chaque primitive (hash, détection de
 * doublons, parsing, extraction DRM/multi-chapitres) existe déjà depuis
 * les Phases 1/2/4 ; ce Use Case ne fait que les enchaîner dans l'ordre
 * qui respecte K2/K7.
 *
 * Contrat :
 * - Entrée : URI SAF d'un fichier sélectionné par l'utilisateur.
 * - Sortie : un [ImportResult] typé — jamais d'exception pour un cas
 *   métier attendu (Blueprint §7.11).
 */
class ImportPublicationUseCase(
    private val publicationParser: PublicationParser,
    private val publicationRepository: PublicationRepository,
    private val fileStorageService: FileStorageService,
) {
    suspend operator fun invoke(fileUri: String): ImportResult {
        // 1. Hash AVANT de parser — evite de parser un doublon inutilement
        //    (le parsing d'un gros EPUB n'est pas gratuit, cf. Blueprint §11.2).
        val hash = fileStorageService.computeSha256(fileUri)
            ?: return ImportResult.Corrupted("Impossible de lire le fichier")

        publicationRepository.getByFileHash(hash)?.let {
            return ImportResult.Duplicate(existingPublicationId = it.id)
        }

        // 2. Parser (gere deja DRM et extraction multi-chapitres, Phases 3/4)
        val parseResult = publicationParser.parse(fileUri)
        val publication = when (parseResult) {
            is ParseResult.DrmProtected -> return ImportResult.DrmProtected(parseResult.message)
            is ParseResult.Corrupted -> return ImportResult.Corrupted(parseResult.message)
            is ParseResult.UnsupportedFormat -> return ImportResult.UnsupportedFormat(parseResult.format)
            is ParseResult.Success -> buildPublication(parseResult, fileUri, hash)
        }

        // 3. Persistance de la permission SAF AVANT insertion — si l'app est
        //    tuee entre les deux, mieux vaut une permission orpheline
        //    qu'une Publication en base pointant vers un URI inaccessible.
        fileStorageService.persistReadPermission(fileUri)
        publicationRepository.insert(publication)

        return ImportResult.Success(publication)
    }

    private suspend fun buildPublication(
        result: ParseResult.Success,
        fileUri: String,
        hash: String,
    ): Publication {
        val metadata: PublicationMetadata = result.metadata
        val size = fileStorageService.getFileSize(fileUri) ?: 0L
        val format = formatOf(fileUri)
        val now = System.currentTimeMillis()

        return Publication(
            id = UUID.randomUUID().toString(),
            // Titre jamais vide (invariant du domaine, Publication.init) —
            // repli sur le nom de fichier si le parseur n'a rien extrait
            // (ex. TXT sans metadonnees, ou EPUB au titre absent de l'OPF).
            title = metadata.title?.takeIf { it.isNotBlank() } ?: fileUri.substringAfterLast('/'),
            subtitle = metadata.subtitle,
            authors = metadata.authors,
            publisher = metadata.publisher,
            language = metadata.language,
            description = metadata.description,
            format = format,
            fileUri = fileUri,
            fileHash = hash,
            fileSize = size,
            chapterCount = result.documentModel.chapters.size,
            seriesName = metadata.seriesName,
            seriesIndex = metadata.seriesIndex,
            subjects = metadata.subjects,
            isDrmProtected = result.isDrmProtected,
            importDate = now,
        )
    }

    // Meme heuristique par extension que CompositePublicationParser
    // (infrastructure/parser) — coherence du choix de format entre
    // "quel parser appeler" et "quel PublicationFormat stocker" (PDF hors
    // perimetre v1, ADR-017 : ni l'un ni l'autre ne le distingue encore).
    private fun formatOf(fileUri: String): PublicationFormat =
        if (fileUri.endsWith(".txt", ignoreCase = true)) PublicationFormat.TXT else PublicationFormat.EPUB
}

sealed interface ImportResult {
    data class Success(val publication: Publication) : ImportResult
    data class Duplicate(val existingPublicationId: String) : ImportResult
    data class DrmProtected(val message: String) : ImportResult
    data class Corrupted(val message: String) : ImportResult
    data class UnsupportedFormat(val format: String) : ImportResult
}
