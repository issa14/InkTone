package com.inktone.infrastructure.parser

import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point d'entrée unique injecté dans le domaine (lié à l'interface
 * PublicationParser via Hilt) — sélectionne le bon parser par extension
 * de fichier. Étendre cette liste pour PDF (Phase 1.x, ADR-017) plutôt
 * que de faire porter la décision de format à chaque appelant.
 */
@Singleton
class CompositePublicationParser @Inject constructor(
    private val readiumParser: ReadiumPublicationParser,
    private val txtParser: TxtPublicationParser,
) : PublicationParser {

    override val supportedFormats = readiumParser.supportedFormats + txtParser.supportedFormats

    override suspend fun parse(fileUri: String): ParseResult {
        val delegate = if (fileUri.endsWith(".txt", ignoreCase = true)) txtParser else readiumParser
        return delegate.parse(fileUri)
    }
}
