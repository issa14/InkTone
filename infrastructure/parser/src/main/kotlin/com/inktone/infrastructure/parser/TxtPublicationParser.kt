package com.inktone.infrastructure.parser

import com.inktone.domain.model.Chapter
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.Paragraph
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.Sentence
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationMetadata
import com.inktone.domain.service.PublicationParser
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Un fichier TXT est traité comme UN SEUL chapitre. Découpage en phrases
 * par une regex simple sur la ponctuation forte (. ! ? suivi d'espace ou
 * fin de ligne) — volontairement naïf : un vrai découpeur linguistique
 * (gestion des abréviations "M.", "etc.") est le travail du pipeline TTS
 * (Blueprint §8.6), pas de ce parser. Ne pas complexifier ici tant qu'un
 * cas réel ne le justifie pas.
 */
@Singleton
class TxtPublicationParser @Inject constructor() : PublicationParser {

    override val supportedFormats = listOf(PublicationFormat.TXT)

    private val sentenceBoundary = Regex("""(?<=[.!?])\s+""")

    override suspend fun parse(fileUri: String): ParseResult {
        val file = File(fileUri)
        if (!file.exists()) return ParseResult.Corrupted("Fichier introuvable: $fileUri")

        val text = runCatching { file.readText(Charsets.UTF_8) }
            .getOrElse { return ParseResult.Corrupted("Lecture impossible (encodage ?): ${it.message}") }

        if (text.isBlank()) return ParseResult.Corrupted("Fichier TXT vide")

        var offset = 0
        val sentences = sentenceBoundary.split(text.trim()).mapIndexed { index, raw ->
            val trimmed = raw.trim()
            val sentence = Sentence(index = index, text = trimmed, startOffset = offset, endOffset = offset + trimmed.length)
            offset += trimmed.length + 1
            sentence
        }.filter { it.text.isNotBlank() }

        val chapter = Chapter(index = 0, href = file.name, title = null, paragraphs = listOf(Paragraph(0, sentences)))
        return ParseResult.Success(
            documentModel = DocumentModel(chapters = listOf(chapter), tableOfContents = emptyList(), resources = emptyList()),
            isDrmProtected = false, // TXT n'a jamais de DRM par définition
            // Un TXT n'a pas de metadonnees embarquees (pas d'OPF) — seul le
            // nom de fichier est disponible comme titre.
            metadata = PublicationMetadata(title = file.nameWithoutExtension),
        )
    }
}
