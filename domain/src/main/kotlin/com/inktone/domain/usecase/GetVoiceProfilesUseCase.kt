package com.inktone.domain.usecase

import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.repository.VoiceProfileRepository

class GetVoiceProfilesUseCase(
    private val voiceProfileRepository: VoiceProfileRepository,
) {
    suspend operator fun invoke(): List<VoiceProfile> = voiceProfileRepository.getAll()
}
