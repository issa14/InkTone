package com.inktone.data.preanalysis

import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.Span
import com.inktone.domain.model.SpanStyles
import com.inktone.domain.model.StyledText
import kotlinx.serialization.Serializable

/**
 * DTOs de sérialisation de la pré-analyse persistée (Lot 22, Palier A).
 *
 * Séparés des modèles de domaine (qui restent purs Kotlin, Blueprint
 * §4.6) — même principe que `com.inktone.data.backup.BackupModels`.
 * Les `globalOffsetRange` (IntRange) et `SpanStyles` (value class) sont
 * aplatis en champs scalaires pour une sérialisation sans serializer
 * dédié.
 */

@Serializable
internal data class PreAnalysisHeader(
    val formatVersion: Int,
    val parserVersion: Int,
    val fileHash: String,
)

@Serializable
internal data class PreAnalysisFile(
    val header: PreAnalysisHeader,
    val chapters: List<ChapterDto>,
)

@Serializable
internal data class ChapterDto(
    val index: Int,
    val href: String,
    val title: String? = null,
    val blocks: List<BookBlockDto>,
    val sentences: List<SentenceDto>,
)

@Serializable
internal data class SentenceDto(
    val index: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val blockIndex: Int = -1,
)

/** `type` discrimine les variants de [BookBlock] (sealed class). */
@Serializable
internal data class BookBlockDto(
    val type: String,
    val richText: StyledTextDto? = null,
    val globalOffsetStart: Int? = null,
    val globalOffsetEnd: Int? = null,
    val isBlockquote: Boolean = false,
    val level: Int? = null,
    val href: String? = null,
    val alt: String? = null,
    val intrinsicWidth: Int? = null,
    val intrinsicHeight: Int? = null,
)

@Serializable
internal data class StyledTextDto(
    val plainText: String,
    val spans: List<SpanDto>,
)

@Serializable
internal data class SpanDto(
    val stylesMask: Int,
    val start: Int,
    val end: Int,
    val href: String? = null,
)

// ── Chapter ↔ ChapterDto ──────────────────────────────────────────────

internal fun Chapter.toDto(): ChapterDto {
    val blocks = (content as? ChapterContent.Rich)?.blocks.orEmpty()
    return ChapterDto(
        index = index,
        href = href,
        title = title,
        blocks = blocks.map { it.toDto() },
        sentences = sentences.map { it.toDto() },
    )
}

internal fun ChapterDto.toDomain(): Chapter = Chapter(
    index = index,
    href = href,
    title = title,
    content = ChapterContent.Rich(blocks = blocks.map { it.toDomain() }),
    sentences = sentences.map { it.toDomain() },
)

internal fun Sentence.toDto(): SentenceDto =
    SentenceDto(index, text, startOffset, endOffset, blockIndex)

internal fun SentenceDto.toDomain(): Sentence =
    Sentence(index, text, startOffset, endOffset, blockIndex)

// ── BookBlock ↔ BookBlockDto ──────────────────────────────────────────

private fun BookBlock.toDto(): BookBlockDto = when (this) {
    is BookBlock.ParagraphBlock -> BookBlockDto(
        type = "paragraph",
        richText = richText.toDto(),
        globalOffsetStart = globalOffsetRange.first,
        globalOffsetEnd = globalOffsetRange.last,
        isBlockquote = isBlockquote,
    )
    is BookBlock.HeadingBlock -> BookBlockDto(
        type = "heading",
        richText = richText.toDto(),
        globalOffsetStart = globalOffsetRange.first,
        globalOffsetEnd = globalOffsetRange.last,
        level = level,
    )
    is BookBlock.ImageBlock -> BookBlockDto(
        type = "image",
        href = href,
        alt = alt,
        intrinsicWidth = intrinsicWidth,
        intrinsicHeight = intrinsicHeight,
    )
    BookBlock.SeparatorBlock -> BookBlockDto(type = "separator")
}

private fun BookBlockDto.toDomain(): BookBlock = when (type) {
    "paragraph" -> BookBlock.ParagraphBlock(
        richText = requireNotNull(richText) { "paragraph sans richText" }.toDomain(),
        globalOffsetRange = range(),
        isBlockquote = isBlockquote,
    )
    "heading" -> BookBlock.HeadingBlock(
        level = requireNotNull(level) { "heading sans level" },
        richText = requireNotNull(richText) { "heading sans richText" }.toDomain(),
        globalOffsetRange = range(),
    )
    "image" -> BookBlock.ImageBlock(
        href = requireNotNull(href) { "image sans href" },
        alt = alt,
        intrinsicWidth = intrinsicWidth,
        intrinsicHeight = intrinsicHeight,
    )
    else -> BookBlock.SeparatorBlock
}

private fun BookBlockDto.range(): IntRange {
    val start = requireNotNull(globalOffsetStart) { "bloc de texte sans globalOffsetStart" }
    val end = requireNotNull(globalOffsetEnd) { "bloc de texte sans globalOffsetEnd" }
    return start..end
}

// ── StyledText / Span ─────────────────────────────────────────────────

private fun StyledText.toDto(): StyledTextDto =
    StyledTextDto(plainText, spans.map { SpanDto(it.styles.mask, it.start, it.end, it.href) })

private fun StyledTextDto.toDomain(): StyledText =
    StyledText(plainText, spans.map { Span(SpanStyles(it.stylesMask), it.start, it.end, it.href) })
