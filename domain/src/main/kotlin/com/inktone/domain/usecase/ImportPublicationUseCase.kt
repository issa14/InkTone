package com.inktone.domain.usecase

import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.FileStorageService
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationMetadata
import com.inktone.domain.service.PublicationParser
import com.inktone.domain.service.SearchService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val searchService: SearchService,
) {
    // Protege la section verification+insertion (Tache 6.3, K2) : plusieurs
    // invocations concurrentes de la meme instance (ImportWorker parallelise,
    // meme importPublication injecte une seule fois) peuvent partager un
    // hash identique. Le parsing (couteux) reste hors verrou.
    private val duplicateCheckMutex = Mutex()

    suspend operator fun invoke(fileUri: String): ImportResult {
        // 1. Hash AVANT de parser — evite de parser un doublon inutilement
        //    (le parsing d'un gros EPUB n'est pas gratuit, cf. Blueprint §11.2).
        val hash = fileStorageService.computeSha256(fileUri)
            ?: return ImportResult.Corrupted("Impossible de lire le fichier")

        // Verification rapide hors verrou - evite de parser un doublon deja
        // connu (le cas courant, non concurrent). Insuffisante seule sous
        // execution parallele (Tache 6.3) : deux imports concurrents du
        // meme hash peuvent tous deux la franchir avant que l'un des deux
        // insere - d'ou la reverification protegee par mutex plus bas.
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
        // Sur par construction : toute branche non-Success du when ci-dessus
        // est retournee immediatement, donc parseResult est forcement
        // ParseResult.Success ici.
        val documentModel = (parseResult as ParseResult.Success).documentModel

        // 3. Reverification + insertion atomiques sous verrou - seule
        // section vraiment critique. Persistance de la permission SAF
        // AVANT insertion — si l'app est tuee entre les deux, mieux vaut
        // une permission orpheline qu'une Publication en base pointant
        // vers un URI inaccessible.
        return duplicateCheckMutex.withLock {
            publicationRepository.getByFileHash(hash)?.let {
                return@withLock ImportResult.Duplicate(existingPublicationId = it.id)
            }
            fileStorageService.persistReadPermission(fileUri)
            publicationRepository.insert(publication)
            // Peuplement de l'index a l'import (Tache 7.3.2, decision
            // actee) : le DocumentModel est deja extrait par le parsing
            // ci-dessus, pas de second passage. Si l'app est tuee entre
            // l'insertion et l'indexation, la publication devient
            // durablement non trouvable par recherche jusqu'a reimport -
            // limite connue, non traitee ici (pas une perte de donnees,
            // juste une fonctionnalite degradee).
            searchService.indexPublication(publication.id, documentModel)
            ImportResult.Success(publication)
        }
    }

    private suspend fun buildPublication(
        result: ParseResult.Success,
        fileUri: String,
        hash: String,
    ): Publication {
        val metadata: PublicationMetadata = result.metadata
        val size = fileStorageService.getFileSize(fileUri) ?: 0L
        val fileName = fileStorageService.getFileName(fileUri) ?: fileUri
        val format = formatOf(fileName)
        val now = System.currentTimeMillis()

        return Publication(
            id = UUID.randomUUID().toString(),
            // Titre jamais vide (invariant du domaine, Publication.init) —
            // repli sur le nom de fichier si le parseur n'a rien extrait
            // (ex. TXT sans metadonnees, ou EPUB au titre absent de l'OPF).
            title = metadata.title?.takeIf { it.isNotBlank() } ?: fileName,
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
            // Lot 12, tache 12.4 : jamais depuis PublicationMetadata (pas
            // ce champ) - derive du DocumentModel deja construit, qui
            // coincide par construction avec chapterCount pour un PDF
            // (page = chapitre, PdfPublicationParser).
            pageCount = result.documentModel.chapters.size.takeIf { format == PublicationFormat.PDF },
            seriesName = metadata.seriesName,
            seriesIndex = metadata.seriesIndex,
            subjects = metadata.subjects,
            isDrmProtected = result.isDrmProtected,
            importDate = now,
            coverUri = metadata.coverUri,
        )
    }

    // Meme heuristique par extension que CompositePublicationParser
    // (infrastructure/parser) — coherence du choix de format entre
    // "quel parser appeler" et "quel PublicationFormat stocker". PDF
    // ajoute au Lot 12 (tache 12.4) - duplication de l'heuristique deja
    // presente avant ce lot entre les deux fichiers, etendue ici plutot
    // que corrigee (ecart declare, decision actee 11 du plan).
    // Prend le nom de fichier resolu (fileStorageService.getFileName),
    // jamais l'URI SAF brute qui ne le contient pas (bug corrige lot 2a).
    private fun formatOf(fileName: String): PublicationFormat = when {
        fileName.endsWith(".txt", ignoreCase = true) -> PublicationFormat.TXT
        fileName.endsWith(".pdf", ignoreCase = true) -> PublicationFormat.PDF
        else -> PublicationFormat.EPUB
    }
}

sealed interface ImportResult {
    data class Success(val publication: Publication) : ImportResult
    data class Duplicate(val existingPublicationId: String) : ImportResult
    data class DrmProtected(val message: String) : ImportResult
    data class Corrupted(val message: String) : ImportResult
    data class UnsupportedFormat(val format: String) : ImportResult
}
