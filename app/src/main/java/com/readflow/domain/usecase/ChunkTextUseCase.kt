package com.readflow.domain.usecase

import com.readflow.domain.model.Sentence
import javax.inject.Inject

/**
 * Découpe un texte brut en phrases françaises.
 * Délègue à [FrenchSentenceSplitter] pour les règles linguistiques.
 *
 * Optimisation GC : aucune allocation de Regex dans le chemin critique.
 * Le sous-découpage des phrases longues utilise un parcours caractère
 * par caractère (split manuel sur virgules) au lieu d'un Regex.split().
 */
class ChunkTextUseCase @Inject constructor() {

    companion object {
        /** Longueur maximale d'une phrase avant sous-découpage. */
        private const val MAX_SENTENCE_LENGTH = 500
    }

    /**
     * Segmente [text] en phrases, avec sous-découpage des phrases
     * dépassant [MAX_SENTENCE_LENGTH] caractères.
     */
    operator fun invoke(text: String): List<Sentence> {
        val sentences = FrenchSentenceSplitter.split(text)

        var globalIdx = 0
        return sentences.flatMap { sentence ->
            if (sentence.text.length > MAX_SENTENCE_LENGTH) {
                subdivideLongSentence(sentence, globalIdx).also {
                    globalIdx += it.size
                }
            } else {
                listOf(sentence.copy(index = globalIdx++))
            }
        }
    }

    /**
     * Sous-découpe une phrase trop longue sur les virgules et points-virgules.
     *
     * Implémentation manuelle sans Regex (split caractère par caractère)
     * pour éviter les allocations de `List<String>` intermédiaires
     * générées par `Regex.split()`.
     */
    private fun subdivideLongSentence(
        sentence: Sentence,
        startIndex: Int
    ): List<Sentence> {
        val result = mutableListOf<Sentence>()
        val text = sentence.text
        var segmentStart = 0
        var localIdx = 0

        for (i in text.indices) {
            val c = text[i]
            // Découpe sur virgule, point-virgule, ou deux-points suivis d'espace
            val isSplitPoint = c == ',' || c == ';' || c == ':'
            if (isSplitPoint && i + 1 < text.length && text[i + 1].isWhitespace()) {
                val segment = text.substring(segmentStart, i + 1).trim()
                if (segment.isNotEmpty()) {
                    result.add(Sentence(
                        index = startIndex + localIdx++,
                        text = segment,
                        startOffset = sentence.startOffset + segmentStart,
                        endOffset = sentence.startOffset + i + 1
                    ))
                }
                segmentStart = i + 1
            }
        }

        // Dernier segment
        val last = text.substring(segmentStart).trim()
        if (last.isNotEmpty()) {
            result.add(Sentence(
                index = startIndex + localIdx,
                text = last,
                startOffset = sentence.startOffset + segmentStart,
                endOffset = sentence.startOffset + text.length
            ))
        }

        return result.ifEmpty {
            listOf(sentence.copy(index = startIndex))
        }
    }
}

