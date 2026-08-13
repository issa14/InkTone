@file:Suppress("RemoveRedundantQualifierName")

package com.inktone.core.testing.fixture

import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.Span
import com.inktone.domain.model.SpanStyles
import com.inktone.domain.model.StyledText

/**
 * DSL compact pour construire des fixtures [BookBlock] dans les tests.
 *
 * ## Usage
 *
 * ```kotlin
 * val chapter = chap(0,
 *     h(1, "Titre du chapitre"),
 *     p("Le " withStyle B, "Petit Prince" withStyle (B or I)),
 *     img("cover.png", "Illustration"),
 *     p("Fin du chapitre."),
 * )
 * ```
 *
 * Les `globalOffsetRange` sont calculés automatiquement : chaque bloc de
 * texte reçoit un intervalle contigu basé sur la concaténation ordonnée
 * des `plainText`.
 *
 * ## Constantes de style
 *
 * | Constante | SpanStyles        |
 * |-----------|-------------------|
 * | B         | STRONG (gras)     |
 * | I         | EMPHASIS (italique)|
 * | U         | INSERTED (souligné)|
 * | S         | DELETED (barré)   |
 * | SUP       | SUPERSCRIPT       |
 * | SUB       | SUBSCRIPT         |
 * | REF       | REFERENCE (lien)  |
 */

// ---- Constantes de style ----

const val B_MASK = 1 shl 0  // STRONG
const val I_MASK = 1 shl 1  // EMPHASIS
const val U_MASK = 1 shl 2  // INSERTED
const val S_MASK = 1 shl 3  // DELETED
const val SUP_MASK = 1 shl 4 // SUPERSCRIPT
const val SUB_MASK = 1 shl 5 // SUBSCRIPT
const val REF_MASK = 1 shl 6 // REFERENCE

val B = SpanStyles(B_MASK)
val I = SpanStyles(I_MASK)
val U = SpanStyles(U_MASK)
val S = SpanStyles(S_MASK)
val SUP = SpanStyles(SUP_MASK)
val SUB = SpanStyles(SUB_MASK)
val REF = SpanStyles(REF_MASK)

// ---- Fonctions de construction ----

/**
 * Construit un [Chapter] avec contenu [ChapterContent.Rich].
 *
 * @param index Index du chapitre dans le spine.
 * @param blocks Blocs de contenu dans l'ordre d'affichage.
 */
fun chap(index: Int = 0, vararg blocks: BookBlock): Chapter {
    // Recalculer les globalOffsetRange pour être cohérents
    var runningOffset = 0
    val adjustedBlocks = blocks.map { block ->
        when (block) {
            is BookBlock.ParagraphBlock -> {
                val len = block.richText.plainText.length
                val adjusted = block.copy(globalOffsetRange = runningOffset until (runningOffset + len))
                runningOffset += len
                adjusted
            }
            is BookBlock.HeadingBlock -> {
                val len = block.richText.plainText.length
                val adjusted = block.copy(globalOffsetRange = runningOffset until (runningOffset + len))
                runningOffset += len
                adjusted
            }
            else -> block
        }
    }
    return Chapter(
        index = index,
        href = "chapter$index.xhtml",
        title = null,
        content = ChapterContent.Rich(blocks = adjustedBlocks),
    )
}

/**
 * Construit un [BookBlock.ParagraphBlock] à partir de segments stylés.
 *
 * @param segments Paires (texte, masque de style). Ex: `p("Le " withStyle B, "Petit Prince" withStyle (B or I))`
 */
fun p(vararg segments: Pair<String, SpanStyles>): BookBlock.ParagraphBlock {
    val plainText = segments.joinToString("") { it.first }
    var offset = 0
    val spans = segments
        .filter { !it.second.isEmpty() }
        .map { (text, style) ->
            val span = Span(styles = style, start = offset, end = offset + text.length)
            offset += text.length
            span
        }
    return BookBlock.ParagraphBlock(
        richText = StyledText(plainText, spans),
        globalOffsetRange = 0 until plainText.length, // Sera recalculé par chap()
    )
}

/** Surcharge : paragraphe sans style. */
fun p(text: String): BookBlock.ParagraphBlock =
    BookBlock.ParagraphBlock(
        richText = StyledText.plain(text),
        globalOffsetRange = 0 until text.length,
    )

/**
 * Construit un [BookBlock.HeadingBlock].
 *
 * @param level Niveau hiérarchique (1–6).
 * @param text Texte du titre.
 */
fun h(level: Int, text: String): BookBlock.HeadingBlock =
    BookBlock.HeadingBlock(
        level = level,
        richText = StyledText.plain(text),
        globalOffsetRange = 0 until text.length,
    )

/**
 * Construit un [BookBlock.ImageBlock].
 */
fun img(
    src: String,
    alt: String? = null,
    width: Int? = null,
    height: Int? = null,
): BookBlock.ImageBlock = BookBlock.ImageBlock(
    href = src,
    alt = alt,
    intrinsicWidth = width,
    intrinsicHeight = height,
)

/** Séparateur. */
fun sep(): BookBlock.SeparatorBlock = BookBlock.SeparatorBlock

// ---- Extensions pour le DSL ----

/** Associe un texte à un style. */
infix fun String.withStyle(style: SpanStyles): Pair<String, SpanStyles> = this to style

/**
 * Construit un [StyledText] à partir de segments stylés.
 *
 * Usage: `styledText("Le " withStyle B, "Petit Prince" withStyle (B or I))`
 */
fun styledText(vararg segments: Pair<String, SpanStyles>): StyledText {
    val plainText = segments.joinToString("") { it.first }
    var offset = 0
    val spans = segments
        .filter { !it.second.isEmpty() }
        .map { (text, style) ->
            val span = Span(styles = style, start = offset, end = offset + text.length)
            offset += text.length
            span
        }
    return StyledText(plainText, spans)
}

/**
 * Construit une [Sentence] de test.
 *
 * @param index Index de la phrase.
 * @param text Texte.
 * @param startOffset Offset de début dans le chapitre.
 * @param blockIndex Index du bloc parent (défaut -1 = pas de bloc).
 */
fun s(index: Int, text: String, startOffset: Int, blockIndex: Int = -1): Sentence =
    Sentence(
        index = index,
        text = text,
        startOffset = startOffset,
        endOffset = startOffset + text.length,
        blockIndex = blockIndex,
    )
