package com.inktone.domain.model

/**
 * Portion de texte avec un style sémantique.
 *
 * Les spans sont NON-chevauchants par construction : deux spans sur le même
 * intervalle de caractères utilisent [SpanStyles] bitmask pour représenter
 * la combinaison (ex: `STRONG | EMPHASIS` pour du gras-italique).
 *
 * Cette garantie est assurée par l'algorithme de normalisation dans
 * [JsoupChapterParser], pas par un `init {}` ici — la validation structurelle
 * est faite au niveau du [StyledText] parent.
 *
 * @param styles Masque de styles sémantiques actifs sur ce segment.
 * @param start Offset de début (inclusif) dans le [StyledText.plainText].
 * @param end Offset de fin (exclusif) dans le [StyledText.plainText].
 * @param href URL cible si [styles] contient [SpanStyles.REFERENCE], null sinon.
 */
data class Span(
    val styles: SpanStyles,
    val start: Int,
    val end: Int,
    val href: String? = null,
) {
    init {
        require(end > start) {
            "Span end ($end) doit être strictement supérieur à start ($start)"
        }
        require(start >= 0) { "Span start ($start) doit être positif ou nul" }
    }
}
