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
}
