package com.inktone.domain.service

import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.PublicationFormat

/**
 * Contrat implémenté par infrastructure/parser (Readium pour EPUB — voir
 * ADR-011 : encapsulé ici, jamais exposé au-delà de ce module).
 */
interface PublicationParser {
    val supportedFormats: List<PublicationFormat>
    suspend fun parse(fileUri: String): ParseResult
}

/**
 * Jamais un échec silencieux (Blueprint §7.11) : chaque cas d'erreur
 * attendu est un type, pas une exception qui remonte au hasard.
 */
sealed interface ParseResult {
    data class Success(val documentModel: DocumentModel, val isDrmProtected: Boolean) : ParseResult
    data class DrmProtected(val message: String) : ParseResult
    data class Corrupted(val message: String) : ParseResult
    data class UnsupportedFormat(val format: String) : ParseResult
}
