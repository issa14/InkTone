package com.inktone.domain.valueobject

/**
 * Position unique dans une publication (Blueprint §3.2).
 *
 * Toute position — reprise de lecture, signet, annotation, résultat de
 * recherche, cible de synchronisation — s'exprime EXCLUSIVEMENT via ce
 * value object. Jamais de numéro de page (revue B5) : [progression] issue
 * de [computeProgression] est une valeur dérivée pour l'affichage,
 * jamais la source de vérité de la reprise de lecture.
 *
 * PDF (Lot 12) se fond dans ce Locator existant plutôt que d'en créer un
 * second (règle non négociable) : `chapterIndex` vaut l'index de page
 * (un `Chapter` par page, `PdfPublicationParser`), `resourceHref` le href
 * réel de cette page (`"page-{index}"`), `charOffset` un décalage dans le
 * texte extrait de la page (`0` par convention pour une page image sans
 * texte). [pageOffsetY] est le seul ajout réel : un ratio de défilement
 * vertical au sein de la page, sans équivalent en EPUB reflowable où la
 * position se déduit entièrement de `charOffset`.
 */
data class Locator(
    val resourceHref: String,
    val chapterIndex: Int,
    val paragraphIndex: Int? = null,
    val charOffset: Int,
    val pageOffsetY: Float? = null,
) : Comparable<Locator> {

    init {
        require(resourceHref.isNotBlank()) { "resourceHref ne peut pas être vide" }
        require(chapterIndex >= 0) { "chapterIndex doit être positif ou nul" }
        require(charOffset >= 0) { "charOffset doit être positif ou nul" }
        paragraphIndex?.let {
            require(it >= 0) { "paragraphIndex doit être positif ou nul" }
        }
        pageOffsetY?.let {
            require(it in 0f..1f) { "pageOffsetY doit être compris entre 0 et 1" }
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
