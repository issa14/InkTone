package com.readflow.domain.repository

import com.readflow.domain.model.SynthesisResult

interface TtsRepository {
    /** Synthétise une phrase en français. */
    suspend fun synthesize(
        text: String,
        voice: Int = 0,
        speed: Float = 1.0f
    ): SynthesisResult
}
