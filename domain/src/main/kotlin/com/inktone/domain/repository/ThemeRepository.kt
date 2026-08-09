package com.inktone.domain.repository

import com.inktone.domain.model.ReadingTheme
import kotlinx.coroutines.flow.Flow

/**
 * Catalogue de thèmes de lecture (Lot 9) — thèmes intégrés
 * ([ReadingTheme.BUILT_IN], constantes en mémoire) + thèmes personnalisés
 * (persistés). [observeAll]/[getById] retournent toujours l'union des
 * deux : aucun consommateur n'a besoin de connaître la distinction pour
 * afficher ou résoudre un thème, seul [ReadingTheme.isBuiltIn] la porte.
 */
interface ThemeRepository {
    fun observeAll(): Flow<List<ReadingTheme>>
    suspend fun getById(id: String): ReadingTheme?

    /** [theme].isBuiltIn doit être `false` — un thème intégré n'est jamais persisté. */
    suspend fun saveCustom(theme: ReadingTheme)
    suspend fun deleteCustom(id: String)
}
