package com.inktone.feature.reader

import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Sentence
import com.inktone.domain.valueobject.Locator

/**
 * Résout une sélection libre au mot (offsets de caractère absolus au
 * chapitre, déjà calés sur des bornes de mot par l'appelant — sélection
 * native de `BasicTextField`, voir `PagedChapterContent.PageBlock`/
 * `ReaderScreen.ParagraphText`) en `Locator` de début/fin.
 */
class AnnotationSelectionHandler {

    /**
     * Lot 21, tâche 6 — `paragraphIndex` renseigné quand les [blocks] du
     * chapitre sont fournis. `charOffset` reste l'ancre de vérité (jamais
     * un second système d'adressage) ; `paragraphIndex` renforce la
     * robustesse (recherche, FTS, reprise partielle). `blocks` vide (ou
     * paramètre omis) → `paragraphIndex = null`, comportement inchangé
     * pour les annotations existantes.
     */
    fun resolveCharRange(
        startOffset: Int,
        endOffsetExclusive: Int,
        chapterIndex: Int,
        resourceHref: String,
        blocks: List<BookBlock> = emptyList(),
    ): Pair<Locator, Locator>? {
        if (endOffsetExclusive <= startOffset) return null
        val startParagraphIndex = blocks.findBlockIndex(startOffset)
        // Le dernier caractère sélectionné (endOffsetExclusive - 1) : une
        // sélection à cheval sur deux blocs renseigne le bloc de FIN sur le
        // Locator de fin, pas celui du début.
        val endParagraphIndex = blocks.findBlockIndex((endOffsetExclusive - 1).coerceAtLeast(startOffset))
        val startLocator = Locator(
            resourceHref = resourceHref, chapterIndex = chapterIndex,
            charOffset = startOffset, paragraphIndex = startParagraphIndex,
        )
        // Convention à connaître avant de consommer `endLocator.paragraphIndex` :
        // `charOffset` reste EXCLUSIF (fin de la sélection), mais
        // `paragraphIndex` désigne le bloc du DERNIER CARACTÈRE
        // sélectionné — pas nécessairement le bloc qui contient
        // `charOffset` lui-même (qui peut être le séparateur inter-blocs
        // ou le début du bloc suivant si la sélection s'arrête pile à la
        // frontière). C'est la sémantique voulue pour une sélection
        // (« quel bloc ai-je sélectionné ? »), pas un bug : ne pas
        // supposer que `charOffset` tombe dans le bloc `paragraphIndex`.
        val endLocator = Locator(
            resourceHref = resourceHref, chapterIndex = chapterIndex,
            charOffset = endOffsetExclusive, paragraphIndex = endParagraphIndex,
        )
        return startLocator to endLocator
    }
}

/**
 * Lot 21, tâche 6 — index du bloc de texte contenant [charOffset], dans
 * la liste COMPLÈTE des blocs (même règle que
 * `JsoupChapterParser.findBlockIndex` : un bloc image/séparateur sans
 * `globalOffsetRange` ne contient jamais de texte, il est ignoré).
 * `null` si aucun bloc ne contient l'offset.
 */
private fun List<BookBlock>.findBlockIndex(charOffset: Int): Int? {
    forEachIndexed { index, block ->
        val range = block.globalOffsetRange
        if (range != null && charOffset in range) return index
    }
    return null
}

/**
 * Palier 3f.1 — substring exacte `[startOffset, endOffsetExclusive)` d'un
 * chapitre reconstruite à partir des offsets de ses [Sentence], pour
 * l'excerpt d'une annotation ET le texte copié ou partagé d'une sélection
 * libre, qui travaillent en offsets de caractère plutôt qu'en index de
 * phrase entière.
 *
 * Le blanc entre deux phrases n'appartient au texte d'aucune [Sentence] :
 * il est restitué par un séparateur — espace dans un même paragraphe, saut
 * de ligne entre deux paragraphes ([Sentence.blockIndex] différent). Sans
 * lui, le texte copié collait les phrases (« monde.Ceci ») et fondait les
 * paragraphes en un seul. Deux phrases contiguës dans le texte (aucun blanc
 * entre elles) restent collées : rien n'y est inventé.
 */
internal fun sliceChapterText(sentences: List<Sentence>, startOffset: Int, endOffsetExclusive: Int): String {
    val builder = StringBuilder()
    var previous: Sentence? = null
    for (sentence in sentences) {
        if (sentence.endOffset <= startOffset) continue
        if (sentence.startOffset >= endOffsetExclusive) break
        val localStart = (startOffset - sentence.startOffset).coerceAtLeast(0)
        val localEnd = (endOffsetExclusive - sentence.startOffset).coerceAtMost(sentence.text.length)
        if (localStart >= localEnd) continue
        val before = previous
        if (before != null && builder.isNotEmpty() && sentence.startOffset > before.endOffset) {
            builder.append(if (sentence.blockIndex != before.blockIndex) '\n' else ' ')
        }
        builder.append(sentence.text, localStart, localEnd)
        previous = sentence
    }
    return builder.toString()
}
