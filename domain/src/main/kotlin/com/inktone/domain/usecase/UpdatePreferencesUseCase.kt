package com.inktone.domain.usecase

import com.inktone.domain.model.UserPreferences
import com.inktone.domain.repository.PreferencesRepository

class UpdatePreferencesUseCase(
    private val preferencesRepository: PreferencesRepository,
) {
    suspend operator fun invoke(preferences: UserPreferences) {
        preferencesRepository.update(preferences)
    }
}
