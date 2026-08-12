package com.inktone.domain.model

/**
 * Texte enrichi avec des annotations de style inline.
 *
 * Invariant : les [spans] sont triés par [Span.start], non-chevauchants, et
 * tous dans les bornes de [plainText]. Ces invariants sont vérifiés dans
 * [init] pour les données venant du parsing Jsoup.
 *
 * @param plainText Le texte brut, sans balises. Source de vérité pour les
 *   offsets TTS, FTS, signets et annotations. Les [Span] sont purement
 *   décoratifs et n'affectent jamais les offsets.
 * @param spans Liste des portions de texte avec un style sémantique.
 */
data class StyledText(
    val plainText: String,
    val spans: List<Span> = emptyList(),
) {
    init {
        require(plainText.isNotEmpty() || spans.isEmpty()) {
            "Un StyledText vide ne peut pas avoir de spans"
        }
        require(spans.all { it.start >= 0 && it.end <= plainText.length }) {
            "Tous les spans doivent être dans les bornes [0, ${plainText.length}]"
        }
        require(spans.all { it.end > it.start }) {
            "Tous les spans doivent avoir end > start"
        }
        // Vérification du non-chevauchement (les spans sont triés par construction,
        // mais on vérifie l'invariant pour les données externes)
        for (i in 1 until spans.size) {
            require(spans[i].start >= spans[i - 1].end) {
                "Spans chevauchants : span[$i].start=${spans[i].start} < span[${i - 1}].end=${spans[i - 1].end}"
            }
        }
    }

    /** Taille mémoire approximative (2 octets par char + 20 octets par span). */
    val approxByteSize: Int
        get() = plainText.length * 2 + spans.size * 20

    companion object {
        /** Construit un [StyledText] sans style à partir d'un texte brut. */
        fun plain(text: String): StyledText = StyledText(text, emptyList())
    }
}
