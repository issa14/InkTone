package com.readflow.domain.usecase

import com.readflow.domain.model.Sentence
import javax.inject.Inject

/**
 * Découpe un texte brut en phrases françaises.
 * Délègue à [FrenchSentenceSplitter] pour les règles linguistiques.
 */
class ChunkTextUseCase @Inject constructor() {

    companion object {
        private const val MAX_SENTENCE_LENGTH = 500
    }

    operator fun invoke(text: String): List<Sentence> {
        val sentences = FrenchSentenceSplitter.split(text)

        // Sous-découpage des phrases trop longues
        return sentences.flatMap { sentence ->
            if (sentence.text.length > MAX_SENTENCE_LENGTH) {
                sentence.text.split(Regex("(?<=,)\\s+"))
                    .mapIndexed { i, part ->
                        val s = part.trim()
                        Sentence(
                            index = sentence.index,
                            text = s,
                            startOffset = sentence.startOffset,
                            endOffset = sentence.endOffset
                        )
                    }
            } else {
                listOf(sentence)
            }
        }
    }
}
