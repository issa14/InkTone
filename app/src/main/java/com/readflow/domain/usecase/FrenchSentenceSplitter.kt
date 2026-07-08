package com.readflow.domain.usecase

import com.readflow.domain.model.Sentence

/**
 * Segmenteur de phrases français avec règles spécifiques :
 * - Abréviations (M., Dr., etc., cf.)
 * - Dialogues (« », guillemets)
 * - Ellipses (...)
 * - Initiales (J. Dupont)
 * - Nombres décimaux (3.14)
 */
object FrenchSentenceSplitter {

    private val ABBREVIATIONS = setOf(
        "M", "MM", "Mme", "Mlle", "Dr", "Pr", "Me", "Mgr",
        "etc", "cf", "vs", "env", "not",
        "janv", "févr", "avr", "juil", "sept", "oct", "nov", "déc"
    )

    fun split(text: String): List<Sentence> {
        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return emptyList()

        val boundaries = findBoundaries(cleaned)
        val sentences = mutableListOf<Sentence>()
        var start = 0

        for (end in boundaries) {
            val s = cleaned.substring(start, end).trim()
            if (s.isNotEmpty()) {
                sentences.add(Sentence(sentences.size, s, start, end))
            }
            start = end
        }

        val last = cleaned.substring(start).trim()
        if (last.isNotEmpty()) {
            sentences.add(Sentence(sentences.size, last, start, cleaned.length))
        }

        return sentences
    }

    private fun findBoundaries(text: String): List<Int> {
        val pattern = Regex("""[.!?](?:\s+|$)(?=[A-ZÀ-Ű«"'(—–-])""")
        return pattern.findAll(text)
            .map { it.range.first }
            .filter { !isFalseBoundary(text, it) }
            .map { it + 1 }
            .toList()
    }

    private fun isFalseBoundary(text: String, dotIndex: Int): Boolean {
        val afterDot = if (dotIndex + 1 < text.length) text[dotIndex + 1] else ' '

        // 1. Abréviation connue
        val wordBefore = extractWordBefore(text, dotIndex)
        for (abbr in ABBREVIATIONS) {
            if (wordBefore.equals(abbr, ignoreCase = true)) return true
        }

        // 2. Nombre décimal (3.14)
        if (dotIndex > 0 && afterDot.isDigit() && text[dotIndex - 1].isDigit()) return true

        // 3. Ellipse (...)
        if (dotIndex >= 2 && text.substring(dotIndex - 2, dotIndex + 1) == "..." &&
            dotIndex + 1 < text.length && text[dotIndex + 1] == '.') return true

        // 4. Initiale (J. K. Rowling) — lettre majuscule isolée suivie d'un point
        if (dotIndex >= 1 && text[dotIndex - 1].isUpperCase() && afterDot.isWhitespace()) {
            val beforeLetter = if (dotIndex >= 2) text[dotIndex - 2] else null
            if (beforeLetter == null || beforeLetter == ' ' || beforeLetter == '.' || beforeLetter == ')' || beforeLetter == '»') {
                return true
            }
        }

        // 5. Point dans dialogue (« Bonjour. »)
        if (afterDot == '»' || afterDot == '"' || afterDot == '\'' || afterDot == ')') return true

        return false
    }

    /** Extrait le mot juste avant le point (sans le point). */
    private fun extractWordBefore(text: String, dotIndex: Int): String {
        val builder = StringBuilder()
        var i = dotIndex - 1
        while (i >= 0 && (text[i].isLetter() || text[i] == '.')) {
            builder.insert(0, text[i])
            i--
        }
        return builder.toString().trimEnd('.')
    }
}
