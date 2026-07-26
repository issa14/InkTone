package com.inktone.domain.usecase

import com.inktone.domain.model.Sentence
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.TtsEngine

/**
 * Démarre la synthèse et la lecture audio d'une phrase.
 *
 * SIGNATURE UNIQUEMENT en Phase 1 — le corps réel exige [TtsEngine]
 * (infrastructure/tts, complété en Phase 5). Ne pas invoquer avant
 * l'injection d'une implémentation réelle.
 *
 * Contrat :
 * - Entrée : la phrase à synthétiser et le profil vocal à utiliser.
 * - Sortie : un [AudioSegment] — jamais d'exception pour un cas métier
 *   attendu (Blueprint §7.11).
 */
class StartAudioReadingUseCase(
    private val ttsEngine: TtsEngine,
) {
    suspend operator fun invoke(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment {
        TODO("Complété en Phase 5 — nécessite un TtsEngine réel (infrastructure/tts)")
    }
}
