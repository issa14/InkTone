package com.inktone.core.testing.fake

import com.inktone.domain.model.UserPreferences
import com.inktone.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePreferencesRepository : PreferencesRepository {
    private val state = MutableStateFlow(UserPreferences())

    override fun observe(): Flow<UserPreferences> = state

    override suspend fun get(): UserPreferences = state.value

    override suspend fun update(preferences: UserPreferences) {
        state.value = preferences
    }
}
