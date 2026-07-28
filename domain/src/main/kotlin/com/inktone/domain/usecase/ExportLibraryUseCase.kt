package com.inktone.domain.usecase

import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.service.FileStorageService
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exporte toute la bibliothèque vers une seule destination SAF choisie par
 * l'utilisateur (Tâche 6.7) — contrat fixé en Phase 1
 * (`invoke(destinationUri)`, pas de sélection livre par livre). Une seule
 * URI de sortie pour plusieurs fichiers sources impose une archive : ZIP
 * plutôt qu'une arborescence SAF (`DocumentsContract.createDocument`),
 * qui exigerait d'étendre `FileStorageService` d'une capacité d'écriture
 * arborescente hors périmètre de la Tâche 6.0.
 *
 * Le fichier ZIP est assemblé dans un fichier temporaire JVM classique
 * (`File.createTempFile` — pur Kotlin, aucune dépendance Android : le
 * domaine ne connaît que [FileStorageService] pour parler SAF), puis
 * écrit vers `destinationUri` via [FileStorageService.writeToUri]
 * (Tâche 6.0).
 */
class ExportLibraryUseCase(
    private val publicationRepository: PublicationRepository,
    private val fileStorageService: FileStorageService,
) {
    suspend operator fun invoke(destinationUri: String): ExportResult {
        val publications = publicationRepository.observeAll().first()
        if (publications.isEmpty()) return ExportResult.Failure("Bibliothèque vide, rien à exporter")

        val archive = File.createTempFile("inktone-export-", ".zip")
        try {
            var exportedCount = 0
            val usedNames = mutableSetOf<String>()
            ZipOutputStream(archive.outputStream()).use { zip ->
                for (publication in publications) {
                    // fileUri est une URI SAF (content://), jamais un chemin
                    // fichier local direct — lecture via
                    // FileStorageService.openInputStream, pas File(uri)
                    // (erreur de type signalée par le plan Phase 6, corrigée ici).
                    val input = fileStorageService.openInputStream(publication.fileUri) ?: continue
                    input.use { stream ->
                        zip.putNextEntry(ZipEntry(uniqueEntryName(publication.title, publication.format, usedNames)))
                        stream.copyTo(zip)
                        zip.closeEntry()
                    }
                    exportedCount++
                }
            }

            if (exportedCount == 0) return ExportResult.Failure("Aucun fichier source lisible")

            val written = fileStorageService.writeToUri(destinationUri, archive)
            return if (written) {
                ExportResult.Success(exportedCount)
            } else {
                ExportResult.Failure("Écriture vers la destination impossible")
            }
        } finally {
            archive.delete()
        }
    }

    private fun uniqueEntryName(title: String, format: PublicationFormat, usedNames: MutableSet<String>): String {
        val extension = when (format) {
            PublicationFormat.EPUB -> "epub"
            PublicationFormat.TXT -> "txt"
            PublicationFormat.PDF -> "pdf"
        }
        val sanitizedTitle = title.replace(Regex("""[/\\:*?"<>|]"""), "_").ifBlank { "publication" }
        var candidate = "$sanitizedTitle.$extension"
        var suffix = 1
        while (!usedNames.add(candidate)) {
            candidate = "$sanitizedTitle ($suffix).$extension"
            suffix++
        }
        return candidate
    }
}

sealed interface ExportResult {
    data class Success(val exportedCount: Int) : ExportResult
    data class Failure(val message: String) : ExportResult
}
