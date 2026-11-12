package com.inktone.domain.model

data class PronunciationRule(
    val id: String,
    val originalText: String,
    val replacementText: String,
    val isRegex: Boolean = false,
    val isEnabled: Boolean = true,
) {
    companion object {
        /**
         * Problème qui empêcherait la règle de s'appliquer, ou `null` si elle
         * est utilisable.
         *
         * `PronunciationRuleApplier` ignore en silence une règle qui lève une
         * exception à la synthèse (« jamais un crash ») : sans cette
         * vérification à la saisie, une expression régulière mal formée était
         * enregistrée, affichée comme active, et n'avait jamais aucun effet.
         */
        fun problemOf(originalText: String, replacementText: String, isRegex: Boolean): PronunciationRuleProblem? {
            if (originalText.isBlank()) return PronunciationRuleProblem.BLANK_ORIGINAL
            if (!isRegex) return null
            val regex = runCatching { Regex(originalText) }.getOrNull()
                ?: return PronunciationRuleProblem.INVALID_PATTERN
            // Un motif qui correspond au texte vide s'appliquerait entre chaque
            // caractère de la phrase : le remplacement serait prononcé partout.
            if (regex.matches("")) return PronunciationRuleProblem.MATCHES_EMPTY_TEXT
            if (!replacementIsValidFor(regex, replacementText)) return PronunciationRuleProblem.INVALID_GROUP_REFERENCE
            return null
        }

        /**
         * Même syntaxe que le remplacement appliqué à la synthèse
         * (`String.replaceFirst(Regex, String)`, règles de `java.util.regex`) :
         * `\` échappe le caractère suivant ; `$n` désigne un groupe — seul le
         * premier chiffre est obligatoire et doit exister dans le motif ;
         * `${nom}` désigne un groupe nommé du motif.
         */
        private fun replacementIsValidFor(regex: Regex, replacement: String): Boolean {
            val groupCount = regex.toPattern().matcher("").groupCount()
            var i = 0
            while (i < replacement.length) {
                when (replacement[i]) {
                    '\\' -> {
                        if (i + 1 >= replacement.length) return false
                        i += 2
                    }
                    '$' -> {
                        val next = replacement.getOrNull(i + 1) ?: return false
                        i = when {
                            next.isDigit() -> {
                                if (next.digitToInt() > groupCount) return false
                                i + 2
                            }
                            next == '{' -> {
                                val end = replacement.indexOf('}', startIndex = i + 2)
                                if (end < 0) return false
                                val name = replacement.substring(i + 2, end)
                                if (name.isEmpty() || !regex.pattern.contains("(?<$name>")) return false
                                end + 1
                            }
                            else -> return false
                        }
                    }
                    else -> i++
                }
            }
            return true
        }
    }
}

/** Raison pour laquelle une règle de prononciation ne peut pas être enregistrée. */
enum class PronunciationRuleProblem {
    BLANK_ORIGINAL,
    INVALID_PATTERN,
    MATCHES_EMPTY_TEXT,
    INVALID_GROUP_REFERENCE,
}
