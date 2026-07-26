package com.inktone.domain.usecase

import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.repository.VoiceProfileRepository

class SaveVoiceProfileUseCase(
    private val voiceProfileRepository: VoiceProfileRepository,
) {
    suspend operator fun invoke(profile: VoiceProfile) {
        voiceProfileRepository.save(profile)
    }
}
