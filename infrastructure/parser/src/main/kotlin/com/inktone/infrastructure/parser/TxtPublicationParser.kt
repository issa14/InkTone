package com.inktone.infrastructure.parser

import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.StyledText
import com.inktone.domain.service.FileStorageService
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationMetadata
import com.inktone.domain.service.PublicationParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Un fichier TXT est traité comme UN SEUL chapitre. Découpage en phrases
 * par une regex simple sur la ponctuation forte (. ! ? suivi d'espace ou
 * fin de ligne) — volontairement naïf : un vrai découpeur linguistique
 * (gestion des abréviations "M.", "etc.") est le travail du pipeline TTS
 * (Blueprint §8.6), pas de ce parser. Ne pas complexifier ici tant qu'un
 * cas réel ne le justifie pas.
 *
 * Bug réel corrigé (lot 2a) : `fileUri` est une URI SAF `content://`, pas
 * un chemin de fichier local — `java.io.File(fileUri)` ne l'ouvre jamais
 * (`exists()` faux), donc tout TXT importé depuis le vrai sélecteur de
 * fichiers échouait silencieusement en `Corrupted`. Passe désormais par
 * [FileStorageService], seule voie correcte pour lire une URI SAF (K5).
 */
@Singleton
class TxtPublicationParser @Inject constructor(
    private val fileStorageService: FileStorageService,
) : PublicationParser {

    override val supportedFormats = listOf(PublicationFormat.TXT)

    private val sentenceBoundary = Regex("""(?<=[.!?])\s+""")

    override suspend fun parse(fileUri: String): ParseResult {
        val text = fileStorageService.openInputStream(fileUri)
            ?.use { stream -> runCatching { stream.readBytes().toString(Charsets.UTF_8) }.getOrNull() }
            ?: return ParseResult.Corrupted("Fichier introuvable ou illisible (encodage ?): $fileUri")

        if (text.isBlank()) return ParseResult.Corrupted("Fichier TXT vide")

        val trimmedText = text.trim()
        var offset = 0
        val sentences = sentenceBoundary.split(trimmedText).mapIndexed { index, raw ->
            val trimmed = raw.trim()
            val sentence = Sentence(index = index, text = trimmed, startOffset = offset, endOffset = offset + trimmed.length)
            offset += trimmed.length + 1
            sentence
        }.filter { it.text.isNotBlank() }

        val fileName = fileStorageService.getFileName(fileUri) ?: fileUri.substringAfterLast('/')
        val titleWithoutExtension = fileName.substringBeforeLast('.', fileName)
        val chapter = Chapter(
            index = 0,
            href = fileName,
            title = null,
            content = ChapterContent.Rich(
                blocks = listOf(
                    BookBlock.ParagraphBlock(
                        richText = StyledText.plain(trimmedText),
                        globalOffsetRange = 0 until trimmedText.length,
                    ),
                ),
            ),
            sentences = sentences,
        )
        return ParseResult.Success(
            documentModel = DocumentModel(chapters = listOf(chapter), tableOfContents = emptyList(), resources = emptyList()),
            isDrmProtected = false, // TXT n'a jamais de DRM par définition
            // Un TXT n'a pas de metadonnees embarquees (pas d'OPF) — seul le
            // nom de fichier est disponible comme titre.
            metadata = PublicationMetadata(title = titleWithoutExtension),
        )
    }
}
