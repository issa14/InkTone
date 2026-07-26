package com.inktone.domain.usecase

import com.inktone.domain.service.TtsEngine

/**
 * Met en pause la lecture audio en cours.
 *
 * SIGNATURE UNIQUEMENT en Phase 1 — le corps réel exige [TtsEngine]
 * (infrastructure/tts, complété en Phase 5). Ne pas invoquer avant
 * l'injection d'une implémentation réelle.
 */
class PauseAudioReadingUseCase(
    private val ttsEngine: TtsEngine,
) {
    suspend operator fun invoke() {
        TODO("Complété en Phase 5 — nécessite un TtsEngine réel (infrastructure/tts)")
    }
}
