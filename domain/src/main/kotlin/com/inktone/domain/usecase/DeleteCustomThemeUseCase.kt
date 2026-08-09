package com.inktone.domain.usecase

import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.repository.ThemeRepository

/**
 * Supprime un thème personnalisé sans jamais laisser de référence
 * orpheline (Lot 9, Tâche 9.5 — zone de danger explicitement signalée :
 * un thème actif supprimé ne doit jamais laisser l'application dans un
 * état invalide).
 *
 * Ordre délibéré — **repli d'abord, suppression ensuite** : toute
 * préférence globale ou surcharge par publication qui pointe vers
 * [themeId] est réécrite vers [ReadingTheme.DEFAULT] AVANT que la ligne
 * ne soit supprimée de `custom_themes`. Si le processus est interrompu
 * entre les deux étapes, le pire état possible est un thème orphelin
 * encore présent en base mais que plus personne ne référence — jamais
 * l'inverse (une référence vers une ligne absente, qui ferait planter la
 * résolution au prochain lancement). Ce n'est pas une transaction SQL
 * atomique multi-repository (domaine sans accès à Room, Blueprint
 * §12.4) : c'est un ordre d'opérations qui rend l'échec partiel
 * sans-danger par construction.
 */
class DeleteCustomThemeUseCase(
    private val themeRepository: ThemeRepository,
    private val preferencesRepository: PreferencesRepository,
    private val readingStateRepository: ReadingStateRepository,
) {
    suspend operator fun invoke(themeId: String) {
        val prefs = preferencesRepository.get()
        if (prefs.theme == themeId) {
            preferencesRepository.update(prefs.copy(theme = ReadingTheme.DEFAULT.id))
        }

        readingStateRepository.getAll()
            .filter { it.overrides?.theme == themeId }
            .forEach { state ->
                readingStateRepository.save(state.copy(overrides = state.overrides?.copy(theme = null)))
            }

        themeRepository.deleteCustom(themeId)
    }
}
