package com.inktone.domain.valueobject

/**
 * Position unique dans une publication (Blueprint §3.2).
 *
 * Toute position — reprise de lecture, signet, annotation, résultat de
 * recherche, cible de synchronisation — s'exprime EXCLUSIVEMENT via ce
 * value object. Jamais de numéro de page (revue B5) : [progression] issue
 * de [computeProgression] est une valeur dérivée pour l'affichage,
 * jamais la source de vérité de la reprise de lecture.
 */
data class Locator(
    val resourceHref: String,
    val chapterIndex: Int,
    val paragraphIndex: Int? = null,
    val charOffset: Int,
) : Comparable<Locator> {

    init {
        require(resourceHref.isNotBlank()) { "resourceHref ne peut pas être vide" }
        require(chapterIndex >= 0) { "chapterIndex doit être positif ou nul" }
        require(charOffset >= 0) { "charOffset doit être positif ou nul" }
        paragraphIndex?.let {
            require(it >= 0) { "paragraphIndex doit être positif ou nul" }
        }
    }

    /**
     * Ordre naturel : par chapitre, puis par offset de caractère. Ne
     * compare jamais `resourceHref` ni `paragraphIndex` seuls — deux
     * Locators du même chapitre s'ordonnent strictement par offset,
     * quel que soit leur `paragraphIndex`.
     */
    override fun compareTo(other: Locator): Int =
        compareValuesBy(this, other, Locator::chapterIndex, Locator::charOffset)

    companion object {
        /**
         * Progression 0..1 pour l'affichage (badge %, barre de
         * progression) UNIQUEMENT. Jamais utilisée pour la reprise —
         * Blueprint §3.2 : "toujours recalculable... sert à l'affichage
         * et à la réconciliation de synchronisation, pas à la reprise."
         */
        fun computeProgression(
            locator: Locator,
            totalCharsBeforeChapter: Int,
            totalCharsInPublication: Int,
        ): Float {
            if (totalCharsInPublication <= 0) return 0f
            val absoluteOffset = totalCharsBeforeChapter + locator.charOffset
            return (absoluteOffset.toFloat() / totalCharsInPublication).coerceIn(0f, 1f)
        }
    }
}
