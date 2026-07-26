package com.inktone.domain.repository

import com.inktone.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    fun observe(): Flow<UserPreferences>
    suspend fun get(): UserPreferences
    suspend fun update(preferences: UserPreferences)
}
