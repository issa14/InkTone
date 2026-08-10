package com.inktone.feature.reader

import com.inktone.domain.model.Sentence
import com.inktone.domain.valueobject.Locator

/**
 * Résout une plage de [Sentence] sélectionnées par index (Tâche 7.0/7.1)
 * en `Locator` de début/fin.
 *
 * Sélection **par phrase**, pas par caractère arbitraire : `Selection` et
 * le `SelectionContainer(selection, onSelectionChange, content)` contrôlé
 * de Compose sont `internal` dans
 * `androidx.compose.foundation:foundation:1.7.2` (vérifié par le
 * compilateur en écrivant cette tâche, pas supposé d'après une doc
 * générique) — aucune API publique ne donne accès aux offsets d'une
 * sélection de texte native. `ReaderScreen` capture donc l'index de
 * `Sentence` touchée directement (appui long/appui simple par phrase),
 * ce qui rend ce resolver trivial : pas de recherche de la `Sentence`
 * contenant un offset arbitraire, l'index est déjà connu par
 * construction.
 */
class AnnotationSelectionHandler {

    fun resolveSelection(
        sentences: List<Sentence>,
        startIndex: Int,
        endIndex: Int,
        chapterIndex: Int,
        resourceHref: String,
    ): Pair<Locator, Locator>? {
        val start = sentences.getOrNull(minOf(startIndex, endIndex)) ?: return null
        val end = sentences.getOrNull(maxOf(startIndex, endIndex)) ?: return null

        val startLocator = Locator(resourceHref = resourceHref, chapterIndex = chapterIndex, charOffset = start.startOffset)
        val endLocator = Locator(resourceHref = resourceHref, chapterIndex = chapterIndex, charOffset = end.endOffset)

        return if (endLocator >= startLocator) startLocator to endLocator else null
    }

    /**
     * Palier 3f.1 — même résolution que [resolveSelection], mais pour une
     * sélection libre au mot : les bornes sont déjà des offsets de
     * caractère absolus (calés sur des bornes de mot par l'appelant), pas
     * des index de [Sentence] entière.
     */
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
