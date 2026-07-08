package com.readflow.data.repository

import com.readflow.domain.model.SynthesisResult
import com.readflow.domain.repository.TtsRepository
import com.readflow.service.onnx.OnnxInferenceService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsRepositoryImpl @Inject constructor(
    private val inferenceService: OnnxInferenceService
) : TtsRepository {

    override suspend fun synthesize(
        text: String,
        voice: Int,
        speed: Float
    ): SynthesisResult {
        val voiceEnum = OnnxInferenceService.Voice.entries
            .find { it.sid == voice } ?: OnnxInferenceService.Voice.JESSICA
        return inferenceService.synthesize(text, voiceEnum, speed)
    }
}
