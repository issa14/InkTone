package com.inktone.domain.valueobject

/**
 * Contexte textuel d'un point d'un chapitre (ADR-027) : les [MAX_CONTEXT_LENGTH]
 * caractères qui précèdent et qui suivent son `charOffset`. Il décrit un point, pas
 * une plage : une annotation garde ses deux `Locator`, chacun avec le sien.
 * Correspond à `Locator.Text(before, null, after)` de Readium (ADR-011).
 */
data class LocatorText(
    val before: String,
    val after: String,
) {
    init {
        require(before.length <= MAX_CONTEXT_LENGTH) { "before ne peut pas dépasser $MAX_CONTEXT_LENGTH caractères" }
        require(after.length <= MAX_CONTEXT_LENGTH) { "after ne peut pas dépasser $MAX_CONTEXT_LENGTH caractères" }
    }

    companion object {
        const val MAX_CONTEXT_LENGTH = 32

        /**
         * Contexte de [charOffset] dans [chapterText], tel que le texte du chapitre
         * est chargé ; moins de caractères en début ou en fin de chapitre. `null`
         * si le texte est vide (page PDF sans texte, chapitre pas encore chargé).
         */
        fun capture(chapterText: String, charOffset: Int): LocatorText? {
            if (chapterText.isEmpty()) return null
            val offset = charOffset.coerceIn(0, chapterText.length)
            return LocatorText(
                before = chapterText.substring((offset - MAX_CONTEXT_LENGTH).coerceAtLeast(0), offset),
                after = chapterText.substring(offset, (offset + MAX_CONTEXT_LENGTH).coerceAtMost(chapterText.length)),
            )
        }
    }
}
