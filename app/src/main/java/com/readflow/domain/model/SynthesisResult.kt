package com.readflow.domain.model

/**
 * Résultat d'une synthèse vocale.
 * Appartient au domain layer (pas de dépendance Android).
 */
data class SynthesisResult(
    val samples: FloatArray,
    val sampleRate: Int,
    val text: String,
    val voiceLabel: String,
    val synthesisTimeMs: Long,
    val audioDurationMs: Long
) {
    val realTimeFactor: Float
        get() = synthesisTimeMs.toFloat() / audioDurationMs.coerceAtLeast(1)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SynthesisResult) return false
        return samples.contentEquals(other.samples) &&
                sampleRate == other.sampleRate && text == other.text
    }

    override fun hashCode(): Int =
        31 * (31 * samples.contentHashCode() + sampleRate) + text.hashCode()
}
