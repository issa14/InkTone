package com.inktone.infrastructure.parser

import com.inktone.domain.service.FileStorageService
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point d'entrée unique injecté dans le domaine (lié à l'interface
 * PublicationParser via Hilt) — sélectionne le bon parser par extension
 * de fichier. Étendre cette liste pour PDF (Phase 1.x, ADR-017) plutôt
 * que de faire porter la décision de format à chaque appelant.
 *
 * Bug réel corrigé (lot 2a) : la détection testait `.endsWith(".txt")`
 * sur `fileUri` lui-même — une URI SAF `content://` est opaque, elle ne
 * contient jamais le nom de fichier, donc un TXT importé depuis le vrai
 * sélecteur de fichiers finissait toujours sur `readiumParser` (échec).
 * Passe par [FileStorageService.getFileName] pour résoudre le vrai nom.
 */
@Singleton
class CompositePublicationParser @Inject constructor(
    private val readiumParser: ReadiumPublicationParser,
    private val txtParser: TxtPublicationParser,
    private val fileStorageService: FileStorageService,
) : PublicationParser {

    override val supportedFormats = readiumParser.supportedFormats + txtParser.supportedFormats

    override suspend fun parse(fileUri: String): ParseResult {
        val fileName = fileStorageService.getFileName(fileUri) ?: fileUri
        val delegate = if (fileName.endsWith(".txt", ignoreCase = true)) txtParser else readiumParser
        return delegate.parse(fileUri)
    }
}
