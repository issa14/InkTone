package com.inktone.domain.usecase

import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.repository.PreferencesRepository

/**
 * Preregalage d'accessibilite en un geste (Tache 8.4, recupere de
 * l'audit UX legacy) : plusieurs reglages appliques ensemble plutot
 * qu'un parcours de plusieurs ecrans separes.
 */
class ApplyAccessibilityPresetUseCase(
    private val preferencesRepository: PreferencesRepository,
) {
    suspend operator fun invoke() {
        val current = preferencesRepository.get()
        preferencesRepository.update(
            current.copy(
                fontSize = 24,
                theme = ReadingTheme.LIGHT,
                fontFamily = FontFamily.OPEN_DYSLEXIC,
                reduceMotion = true,
            ),
        )
    }
}
