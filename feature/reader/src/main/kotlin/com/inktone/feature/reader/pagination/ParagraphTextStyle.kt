package com.inktone.feature.reader.pagination

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.inktone.domain.model.ParagraphStyle

/**
 * Mapping unique `ParagraphStyle` (domaine, Blueprint) → attributs
 * visuels de mesure/rendu. Source commune à `ChapterTextMeasurer` (3a.1)
 * et au rendu du mode pagé (3a.2), pour ne jamais avoir deux endroits où
 * un titre EPUB pourrait être stylé différemment selon qu'on mesure ou
 * qu'on affiche — c'est exactement la classe de bug que corrige 3a.2
 * (`ParagraphStyle.NORMAL` en dur).
 */
fun spanStyleFor(style: ParagraphStyle): SpanStyle = when (style) {
    ParagraphStyle.HEADING -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.25.em)
    ParagraphStyle.BLOCK_QUOTE -> SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    ParagraphStyle.POEM_LINE -> SpanStyle()
    ParagraphStyle.NORMAL -> SpanStyle()
}
