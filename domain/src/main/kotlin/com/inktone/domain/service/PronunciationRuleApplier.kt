package com.inktone.domain.service

import com.inktone.domain.repository.PronunciationRuleRepository
import kotlinx.coroutines.flow.first

/**
 * Applique les regles de prononciation (Tache 8.3) avant la synthese,
 * pas a l'extraction — reste reversible si l'utilisateur modifie une
 * regle sans reimporter le livre.
 *
 * Retourne un [AppliedText] plutot qu'un simple `String` : le texte
 * envoye au moteur TTS peut differer en longueur du texte affiche a
 * l'ecran (« Dr. » -> « Docteur »), et les `WordTimestamp` doivent
 * rester alignes sur le texte ORIGINAL (celui affiche, voir
 * `AudioSegment`/ADR-013 — jamais un surlignage qui pointe vers le texte
 * substitue envoye au moteur).
 */
class PronunciationRuleApplier(
    private val ruleRepository: PronunciationRuleRepository,
) {
    suspend fun apply(text: String): AppliedText {
        val rules = ruleRepository.observeAll().first().filter { it.isEnabled }
        return rules.fold(AppliedText.identity(text)) { acc, rule ->
            if (rule.isRegex) {
                runCatching { acc.applyRegexRule(rule.originalText, rule.replacementText) }
                    .getOrElse { acc } // regex invalide utilisateur -> ignoree, jamais un crash
            } else {
                acc.applyLiteralRule(rule.originalText, rule.replacementText)
            }
        }
    }
}

/**
 * `substitutedText` : ce qui est effectivement envoye au moteur TTS.
 * `originalText` : ce qui est affiche a l'ecran (source du surlignage).
 * `originOffsets[i]` = offset dans `originalText` d'ou provient le
 * caractere `substitutedText[i]` (les caracteres de remplacement
 * heritent tous de l'offset de DEBUT du texte remplace).
 */
class AppliedText private constructor(
    val originalText: String,
    val substitutedText: String,
    private val originOffsets: IntArray,
) {
    /** Offset dans [originalText] correspondant a un offset dans [substitutedText]. */
    fun originalOffsetFor(substitutedOffset: Int): Int {
        if (originOffsets.isEmpty()) return 0
        val clamped = substitutedOffset.coerceIn(0, originOffsets.size - 1)
        return originOffsets[clamped]
    }

    /** Fin d'intervalle : un offset egal a la longueur du texte pointe juste apres le dernier caractere. */
    fun originalEndOffsetFor(substitutedEndOffsetExclusive: Int): Int {
        if (substitutedEndOffsetExclusive >= originOffsets.size) return originalText.length
        if (substitutedEndOffsetExclusive <= 0) return 0
        return originOffsets[substitutedEndOffsetExclusive]
    }

    internal fun applyLiteralRule(from: String, to: String): AppliedText {
        if (from.isEmpty()) return this
        val newSubstituted = StringBuilder()
        val newOffsets = mutableListOf<Int>()
        var i = 0
        while (i < substitutedText.length) {
            if (substitutedText.startsWith(from, i)) {
                val originStart = originOffsets[i]
                repeat(to.length) { newOffsets.add(originStart) }
                newSubstituted.append(to)
                i += from.length
            } else {
                newSubstituted.append(substitutedText[i])
                newOffsets.add(originOffsets[i])
                i++
            }
        }
        return AppliedText(originalText, newSubstituted.toString(), newOffsets.toIntArray())
    }

    internal fun applyRegexRule(pattern: String, replacement: String): AppliedText {
        val regex = Regex(pattern)
        val newSubstituted = StringBuilder()
        val newOffsets = mutableListOf<Int>()
        var lastEnd = 0
        for (match in regex.findAll(substitutedText)) {
            for (i in lastEnd until match.range.first) {
                newSubstituted.append(substitutedText[i])
                newOffsets.add(originOffsets[i])
            }
            val originStart = originOffsets.getOrElse(match.range.first) { originalText.length }
            val resolvedReplacement = match.value.replaceFirst(regex, replacement)
            repeat(resolvedReplacement.length) { newOffsets.add(originStart) }
            newSubstituted.append(resolvedReplacement)
            lastEnd = match.range.last + 1
        }
        for (i in lastEnd until substitutedText.length) {
            newSubstituted.append(substitutedText[i])
            newOffsets.add(originOffsets[i])
        }
        return AppliedText(originalText, newSubstituted.toString(), newOffsets.toIntArray())
    }

    companion object {
        fun identity(text: String): AppliedText = AppliedText(text, text, IntArray(text.length) { it })
    }
}

/**
 * Remappe un [WordTimestamp] issu de la synthese (offsets dans le texte
 * SUBSTITUE envoye au moteur) vers le texte ORIGINAL affiche a l'ecran —
 * jamais l'inverse (Tache 8.3, point d'attention explicite du plan).
 */
fun WordTimestamp.remapToOriginal(applied: AppliedText): WordTimestamp {
    val originalStart = applied.originalOffsetFor(charOffset)
    val originalEndExclusive = applied.originalEndOffsetFor(charOffset + word.length)
        .coerceAtLeast(originalStart)
        .coerceAtMost(applied.originalText.length)
    val originalWord = applied.originalText.substring(originalStart, originalEndExclusive)
    return copy(charOffset = originalStart, word = originalWord.ifEmpty { word })
}
