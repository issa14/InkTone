package com.inktone.domain.repository

import com.inktone.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    fun observe(): Flow<UserPreferences>
    suspend fun get(): UserPreferences
    suspend fun update(preferences: UserPreferences)

    /**
     * Applique [transform] aux préférences actuelles, lecture et écriture
     * comprises, sans qu'une autre mise à jour passée par cette méthode ne puisse
     * s'intercaler. « Lire toutes les préférences, puis réécrire toute la ligne »
     * dans des coroutines indépendantes laissait deux écritures rapprochées
     * s'écraser : la seconde réécrivait l'ancienne valeur du champ que la
     * première venait de changer (audit UX D67).
     */
    suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        update(transform(get()))
    }
}
