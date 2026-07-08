package com.readflow.domain.usecase

import com.readflow.domain.model.SynthesisResult
import com.readflow.domain.repository.TtsRepository
import javax.inject.Inject

/** Synthétise un texte en audio via le moteur TTS. */
class SynthesizeUseCase @Inject constructor(
    private val ttsRepository: TtsRepository
) {
    suspend operator fun invoke(
        text: String,
        voice: Int = 0,
        speed: Float = 1.0f
    ): SynthesisResult {
        require(text.isNotBlank()) { "Le texte ne peut pas être vide" }
        return ttsRepository.synthesize(text, voice, speed)
    }
}
