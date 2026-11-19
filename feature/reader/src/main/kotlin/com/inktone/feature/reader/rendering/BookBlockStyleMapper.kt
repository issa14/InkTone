package com.inktone.feature.reader.rendering

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import com.inktone.domain.model.BlockAlignment
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.SpanStyles
import com.inktone.domain.model.StyledText

/**
 * Mapping sémantique → visuel pour les [SpanStyles] et [BookBlock].
 *
 * SEUL endroit où la sémantique du domaine devient du visuel Compose.
 * Les autres couches (ReaderScreen, ChapterTextMeasurer) appellent ces
 * fonctions sans jamais interpréter elles-mêmes les styles.
 *
 * ## Règle
 *
 * Aucun autre fichier de `feature/reader` n'importe [SpanStyles] pour
 * faire du mapping visuel. Tout passe par ce mapper.
 */
object BookBlockStyleMapper {

    // ---- Mapping SpanStyles → SpanStyle ----

    /**
     * Convertit un masque [SpanStyles] en [SpanStyle] Compose.
     *
     * Les styles sont cumulatifs : `STRONG | EMPHASIS` produit
     * `SpanStyle(fontWeight = Bold, fontStyle = Italic)`.
     */
    fun spanStyleFor(styles: SpanStyles): SpanStyle {
        if (styles.isEmpty()) return SpanStyle()

        return SpanStyle(
            fontWeight = if (SpanStyles.STRONG in styles) FontWeight.Bold else null,
            fontStyle = if (SpanStyles.EMPHASIS in styles) FontStyle.Italic else null,
            textDecoration = buildTextDecoration(styles),
            baselineShift = buildBaselineShift(styles),
            fontSize = buildFontSize(styles),
            // Lien : couleur du texte du thème, signalé par le soulignement. Un bleu
            // fixe sortait de la charte et perdait en contraste sur les thèmes sombres.
        )
    }

    // ---- Mapping BookBlock → TextStyle ----

    /**
     * Style de texte de base pour un [BookBlock].
     *
     * @param block Le bloc à styler.
     * @param baseStyle Le style de base (taille de police, police, couleur
     *   du thème de lecture).
     */
    fun textStyleFor(block: BookBlock, baseStyle: TextStyle): TextStyle {
        val styled = when {
            block is BookBlock.HeadingBlock -> headingStyle(block.level, baseStyle)
            block is BookBlock.ParagraphBlock && block.isBlockquote -> baseStyle.copy(fontStyle = FontStyle.Italic)
            else -> baseStyle
        }
        val aligned = when (block.alignment) {
            BlockAlignment.CENTER -> styled.copy(textAlign = TextAlign.Center)
            BlockAlignment.END -> styled.copy(textAlign = TextAlign.End)
            BlockAlignment.START -> styled
        }
        return if (block.firstLineIndentEm > 0f) {
            aligned.copy(textIndent = TextIndent(firstLine = block.firstLineIndentEm.em))
        } else {
            aligned
        }
    }

    /** Agrandissement d'un titre de niveau [level] ; partagé avec le mode paginé. */
    fun headingScale(level: Int): Float = when (level) {
        1 -> 1.5f
        2 -> 1.25f
        else -> 1.1f // h3–h6 : légèrement plus grand que le corps
    }

    /** Style pour un titre de niveau [level] (1–6). */
    private fun headingStyle(level: Int, base: TextStyle): TextStyle {
        val fontSizeMultiplier = headingScale(level)
        // L'interligne suit la taille : depuis que le style de base porte un
        // `lineHeight` ABSOLU (en sp, réglage utilisateur), grossir la seule
        // police ferait se chevaucher les lignes d'un titre long, qui garderait
        // l'interligne du corps de texte.
        return base.copy(
            fontSize = base.fontSize * fontSizeMultiplier,
            lineHeight = if (base.lineHeight.isSp) base.lineHeight * fontSizeMultiplier else base.lineHeight,
            fontWeight = FontWeight.Bold,
        )
    }

    // ---- Helpers ----

    private fun buildTextDecoration(styles: SpanStyles): TextDecoration? {
        var hasUnderline = false
        var hasLineThrough = false
        if (SpanStyles.INSERTED in styles || SpanStyles.REFERENCE in styles) hasUnderline = true
        if (SpanStyles.DELETED in styles) hasLineThrough = true
        return when {
            hasUnderline && hasLineThrough -> TextDecoration.combine(
                listOf(TextDecoration.Underline, TextDecoration.LineThrough),
            )
            hasUnderline -> TextDecoration.Underline
            hasLineThrough -> TextDecoration.LineThrough
            else -> null
        }
    }

    private fun buildBaselineShift(styles: SpanStyles): BaselineShift? = when {
        SpanStyles.SUPERSCRIPT in styles -> BaselineShift.Superscript
        SpanStyles.SUBSCRIPT in styles -> BaselineShift.Subscript
        else -> null
    }

    private fun buildFontSize(styles: SpanStyles): TextUnit = when {
        SpanStyles.SUPERSCRIPT in styles || SpanStyles.SUBSCRIPT in styles -> 0.7.em
        else -> TextUnit.Unspecified
    }

    // ---- Build AnnotatedString (partagé avec ChapterTextMeasurer) ----

    /**
     * Construit un [androidx.compose.ui.text.AnnotatedString] à partir
     * d'un [StyledText] avec ses spans appliqués. Le style de base
     * (taille, couleur) est appliqué au niveau du composable ou du
     * `TextMeasurer`, pas ici.
     */
    internal fun buildAnnotatedString(richText: StyledText): androidx.compose.ui.text.AnnotatedString =
        buildAnnotatedString {
            val plainText = richText.plainText.withUnbreakableElisions()
            val spans = richText.spans
            var lastEnd = 0
            for (span in spans) {
                if (span.start > lastEnd) {
                    append(plainText.substring(lastEnd, span.start))
                }
                val spanStyle = spanStyleFor(span.styles)
                appendLinkable(span.href.takeIf { SpanStyles.REFERENCE in span.styles }) {
                    withStyle(spanStyle) {
                        append(plainText.substring(span.start, span.end))
                    }
                }
                lastEnd = span.end
            }
            if (lastEnd < plainText.length) {
                append(plainText.substring(lastEnd))
            }
        }
}
