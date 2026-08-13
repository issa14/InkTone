package com.inktone.domain.model

/**
 * Masque de bits pour les styles de texte inline.
 *
 * Chaque bit représente un style sémantique indépendant. L'utilisation d'un
 * bitmask plutôt que d'une `Set<Style>` ou d'une sealed class évite
 * l'allocation par span et permet le test de combinaison en O(1) via
 * `styles.contains(STRONG)`.
 *
 * ## Mapping sémantique → visuel
 *
 * Le mapping de ces styles vers des [androidx.compose.ui.text.SpanStyle]
 * (gras, italique, soulignement, etc.) est UNIQUEMENT fait dans la couche
 * présentation (`BookBlockStyleMapper`). Le domaine reste pur.
 *
 * ## Correspondance HTML
 *
 * | Style       | Éléments HTML        |
 * |-------------|---------------------|
 * | STRONG      | `<strong>`, `<b>`    |
 * | EMPHASIS    | `<em>`, `<i>`        |
 * | INSERTED    | `<ins>`, `<u>`       |
 * | DELETED     | `<del>`, `<s>`       |
 * | SUPERSCRIPT | `<sup>`              |
 * | SUBSCRIPT   | `<sub>`              |
 * | REFERENCE   | `<a href>`           |
 */
@JvmInline
value class SpanStyles(val mask: Int) {
    companion object {
        val NONE = SpanStyles(0)
        val STRONG = SpanStyles(1 shl 0)
        val EMPHASIS = SpanStyles(1 shl 1)
        val INSERTED = SpanStyles(1 shl 2)
        val DELETED = SpanStyles(1 shl 3)
        val SUPERSCRIPT = SpanStyles(1 shl 4)
        val SUBSCRIPT = SpanStyles(1 shl 5)
        val REFERENCE = SpanStyles(1 shl 6)
    }

    /** Combine deux masques (OU binaire). */
    operator fun plus(other: SpanStyles): SpanStyles = SpanStyles(mask or other.mask)

    /** Vérifie si ce masque contient [other]. */
    operator fun contains(other: SpanStyles): Boolean = (mask and other.mask) == other.mask

    /** Vrai si aucun style n'est actif. */
    fun isEmpty(): Boolean = mask == 0

    override fun toString(): String = buildString {
        append("SpanStyles(")
        val parts = mutableListOf<String>()
        if (STRONG in this@SpanStyles) parts += "STRONG"
        if (EMPHASIS in this@SpanStyles) parts += "EMPHASIS"
        if (INSERTED in this@SpanStyles) parts += "INSERTED"
        if (DELETED in this@SpanStyles) parts += "DELETED"
        if (SUPERSCRIPT in this@SpanStyles) parts += "SUPERSCRIPT"
        if (SUBSCRIPT in this@SpanStyles) parts += "SUBSCRIPT"
        if (REFERENCE in this@SpanStyles) parts += "REFERENCE"
        append(parts.joinToString("|"))
        append(")")
    }
}
