package com.inktone.infrastructure.parser

import com.inktone.domain.service.FileStorageService
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point d'entrée unique injecté dans le domaine (lié à l'interface
 * PublicationParser via Hilt) — sélectionne le bon parser par extension
 * de fichier.
 *
 * Bug réel corrigé (lot 2a) : la détection testait `.endsWith(".txt")`
 * sur `fileUri` lui-même — une URI SAF `content://` est opaque, elle ne
 * contient jamais le nom de fichier, donc un TXT importé depuis le vrai
 * sélecteur de fichiers finissait toujours sur `readiumParser` (échec).
 * Passe par [FileStorageService.getFileName] pour résoudre le vrai nom.
 *
 * PDF ajouté au Lot 12 (tâche 12.2) — même heuristique par extension que
 * `ImportPublicationUseCase.formatOf` (domain), dette de duplication déjà
 * présente avant ce lot et étendue ici plutôt que corrigée (écart déclaré,
 * voir décision actée 11 du plan).
 */
@Singleton
class CompositePublicationParser @Inject constructor(
    private val readiumParser: ReadiumPublicationParser,
    private val txtParser: TxtPublicationParser,
    private val pdfParser: PdfPublicationParser,
    private val fileStorageService: FileStorageService,
) : PublicationParser {

    override val supportedFormats = readiumParser.supportedFormats + txtParser.supportedFormats + pdfParser.supportedFormats

    override suspend fun parse(fileUri: String): ParseResult {
        val fileName = fileStorageService.getFileName(fileUri) ?: fileUri
        val delegate = when {
            fileName.endsWith(".txt", ignoreCase = true) -> txtParser
            fileName.endsWith(".pdf", ignoreCase = true) -> pdfParser
            else -> readiumParser
        }
        return delegate.parse(fileUri)
    }
}
