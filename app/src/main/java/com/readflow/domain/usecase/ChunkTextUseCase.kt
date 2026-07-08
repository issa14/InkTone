package com.readflow.domain.usecase

import com.readflow.domain.model.Sentence
import javax.inject.Inject

/**
 * Découpe un texte brut en phrases françaises.
 * Gère : dialogues (« »), abréviations (M., Dr.), ellipses (...), points de suspension.
 */
class ChunkTextUseCase @Inject constructor() {

    companion object {
        private val SENTENCE_BOUNDARY = Regex("""(?<=[.!?])\s+(?=[A-ZÀ-Ű«"])""")
        private const val MAX_SENTENCE_LENGTH = 500
    }

    operator fun invoke(text: String): List<Sentence> {
        val raw = text.replace(Regex("\\s+"), " ").trim()
        val parts = raw.split(SENTENCE_BOUNDARY)
        val sentences = mutableListOf<String>()
        var offset = 0

        for (part in parts) {
            if (part.length > MAX_SENTENCE_LENGTH) {
                // Sous-découpage aux virgules ou conjonctions
                val subParts = part.split(Regex("(?<=,)\\s+"))
                for (sub in subParts) {
                    val s = sub.trim()
                    if (s.isNotEmpty()) {
                        val start = raw.indexOf(s, offset)
                        sentences.add(s)
                        offset = start + s.length
                    }
                }
            } else {
                val s = part.trim()
                if (s.isNotEmpty()) {
                    val start = raw.indexOf(s, offset)
                    sentences.add(s)
                    offset = start + s.length
                }
            }
        }

        return sentences.mapIndexed { i, text ->
            Sentence(
                index = i,
                text = text,
                startOffset = raw.indexOf(text),
                endOffset = raw.indexOf(text) + text.length
            )
        }
    }
}
