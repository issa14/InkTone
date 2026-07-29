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
    data class Success(
        val documentModel: DocumentModel,
        val isDrmProtected: Boolean,
        val metadata: PublicationMetadata = PublicationMetadata(),
    ) : ParseResult
    data class DrmProtected(val message: String) : ParseResult
    data class Corrupted(val message: String) : ParseResult
    data class UnsupportedFormat(val format: String) : ParseResult
}

/**
 * Métadonnées portées par [ParseResult.Success] (Tâche 6.1.1) — déjà
 * réduites aux types primitifs du domaine ici : `ReadiumPublicationParser`
 * fait la traduction depuis `Metadata`/`Contributor`/`Subject` (Readium),
 * jamais exposés au-delà d'infrastructure/parser (ADR-011).
 */
data class PublicationMetadata(
    val title: String? = null,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val language: String? = null,
    val description: String? = null,
    val seriesName: String? = null,
    val seriesIndex: Float? = null,
    val subjects: List<String> = emptyList(),
)
