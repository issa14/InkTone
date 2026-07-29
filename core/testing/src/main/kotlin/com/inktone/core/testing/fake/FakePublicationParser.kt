package com.inktone.core.testing.fake

import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationMetadata
import com.inktone.domain.service.PublicationParser
import kotlinx.coroutines.delay

/**
 * Retourne toujours le même [ParseResult], configurable par test — pas de
 * vrai parsing (réservé aux tests `androidTest` d'infrastructure/parser,
 * qui exigent Readium et un contexte Android réel).
 *
 * [delayMs] : point de suspension artificiel (défaut aucun) — sert à
 * élargir volontairement la fenêtre de course entre deux `parse()`
 * concurrents dans les tests de parallélisation (Tâche 6.3), le fake
 * n'ayant sinon aucune IO réelle pour interleaver.
 */
class FakePublicationParser(
    private var result: ParseResult = ParseResult.Success(
        documentModel = DocumentModel(chapters = emptyList(), tableOfContents = emptyList(), resources = emptyList()),
        isDrmProtected = false,
        metadata = PublicationMetadata(title = "Titre de test"),
    ),
    private val delayMs: Long = 0,
) : PublicationParser {
    override val supportedFormats = listOf(PublicationFormat.EPUB)

    fun setNextResult(result: ParseResult) {
        this.result = result
    }

    override suspend fun parse(fileUri: String): ParseResult {
        if (delayMs > 0) delay(delayMs)
        return result
    }
}
