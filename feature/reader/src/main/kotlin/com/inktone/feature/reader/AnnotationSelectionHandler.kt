package com.inktone.feature.reader

import com.inktone.domain.model.Sentence
import com.inktone.domain.valueobject.Locator

/**
 * Résout une sélection libre au mot (offsets de caractère absolus au
 * chapitre, déjà calés sur des bornes de mot par l'appelant — sélection
 * native de `BasicTextField`, voir `PagedChapterContent.PageBlock`/
 * `ReaderScreen.ParagraphText`) en `Locator` de début/fin.
 */
class AnnotationSelectionHandler {

    fun resolveCharRange(
        startOffset: Int,
        endOffsetExclusive: Int,
        chapterIndex: Int,
        resourceHref: String,
    ): Pair<Locator, Locator>? {
        if (endOffsetExclusive <= startOffset) return null
        val startLocator = Locator(resourceHref = resourceHref, chapterIndex = chapterIndex, charOffset = startOffset)
        val endLocator = Locator(resourceHref = resourceHref, chapterIndex = chapterIndex, charOffset = endOffsetExclusive)
        return startLocator to endLocator
    }
}

/**
 * Palier 3f.1 — substring exacte `[startOffset, endOffsetExclusive)` d'un
 * chapitre reconstruite à partir des offsets de ses [Sentence], pour
 * l'excerpt/le texte copié d'une sélection libre qui travaille en offsets
 * de caractère plutôt qu'en index de phrase entière. Les espaces entre
 * phrases (hors texte de chaque `Sentence`) ne sont pas restitués — sans
 * incidence, ceci ne sert qu'à un aperçu tronqué, jamais à une réécriture.
 */
internal fun sliceChapterText(sentences: List<Sentence>, startOffset: Int, endOffsetExclusive: Int): String {
    val builder = StringBuilder()
    for (sentence in sentences) {
        if (sentence.endOffset <= startOffset) continue
        if (sentence.startOffset >= endOffsetExclusive) break
        val localStart = (startOffset - sentence.startOffset).coerceAtLeast(0)
        val localEnd = (endOffsetExclusive - sentence.startOffset).coerceAtMost(sentence.text.length)
        if (localStart < localEnd) builder.append(sentence.text, localStart, localEnd)
    }
    return builder.toString()
}
