package com.inktone.domain.valueobject

/** Résultat du ré-ancrage d'une position dans le nouveau texte d'un chapitre. */
data class ReanchoredLocator(
    val locator: Locator,
    /** Faux quand le contexte n'a pas été retrouvé : l'offset est seulement borné, la position comptée comme non ré-ancrée. */
    val anchored: Boolean,
)

/**
 * Recalcule le `charOffset` d'un [Locator] quand le texte extrait de son chapitre a
 * changé (ADR-027, décision 4). Les espaces sont normalisés des deux côtés à la
 * comparaison, jamais au stockage. Ordre : `before + after`, puis `before` seul,
 * puis `after` seul ; plusieurs occurrences : la plus proche de l'ancien offset ;
 * rien : l'offset est conservé, borné à la longueur du chapitre. Une position n'est
 * jamais supprimée. Le contexte est ensuite recapturé dans le nouveau texte.
 */
object LocatorReanchoring {

    fun reanchor(locator: Locator, newChapterText: String): ReanchoredLocator {
        val context = locator.text
        val bounded = locator.charOffset.coerceIn(0, newChapterText.length)
        val found = context?.let { findOffset(it, locator.charOffset, newChapterText) }
        val offset = found ?: bounded
        val updated = locator.copy(
            charOffset = offset,
            text = LocatorText.capture(newChapterText, offset),
        )
        return ReanchoredLocator(updated, anchored = found != null)
    }

    private fun findOffset(context: LocatorText, oldOffset: Int, text: String): Int? {
        val haystack = NormalizedText.of(text)
        val before = normalize(context.before)
        val after = normalize(context.after)
        // 1. before + after : le point est la frontière entre les deux.
        if (before.isNotEmpty() && after.isNotEmpty()) {
            closest(haystack, before + after, before.length, oldOffset)?.let { return it }
        }
        // 2. before seul : le point est juste après.
        if (before.isNotEmpty()) closest(haystack, before, before.length, oldOffset)?.let { return it }
        // 3. after seul : le point est au début.
        if (after.isNotEmpty()) closest(haystack, after, 0, oldOffset)?.let { return it }
        return null
    }

    /** Occurrence de [needle] dont le point (à [pointInNeedle] de son début) est le plus proche de [oldOffset]. */
    private fun closest(haystack: NormalizedText, needle: String, pointInNeedle: Int, oldOffset: Int): Int? {
        var best: Int? = null
        var from = 0
        while (true) {
            val index = haystack.text.indexOf(needle, from)
            if (index < 0) break
            val original = haystack.originalOffsetOf(index + pointInNeedle)
            if (best == null || kotlin.math.abs(original - oldOffset) < kotlin.math.abs(best - oldOffset)) best = original
            from = index + 1
        }
        return best
    }

    private fun normalize(value: String): String = NormalizedText.of(value).text

    /** Texte aux espaces regroupés (suite d'espaces = une espace, sans ôter les bords), avec le retour à l'offset d'origine. */
    private class NormalizedText(val text: String, private val originalIndex: IntArray, private val originalLength: Int) {

        /** Offset d'origine du caractère normalisé [index] ; la longueur d'origine au-delà de la fin. */
        fun originalOffsetOf(index: Int): Int = if (index >= originalIndex.size) originalLength else originalIndex[index]

        companion object {
            fun of(value: String): NormalizedText {
                val out = StringBuilder()
                val map = ArrayList<Int>(value.length)
                var previousWasSpace = false
                value.forEachIndexed { i, c ->
                    if (c.isWhitespace()) {
                        if (!previousWasSpace) {
                            out.append(' ')
                            map.add(i)
                        }
                        previousWasSpace = true
                    } else {
                        out.append(c)
                        map.add(i)
                        previousWasSpace = false
                    }
                }
                return NormalizedText(out.toString(), map.toIntArray(), value.length)
            }
        }
    }
}
