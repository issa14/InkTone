package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.infrastructure.database.dao.UserPreferencesDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class RoomPreferencesRepository @Inject constructor(
    private val dao: UserPreferencesDao,
    private val startupCache: StartupPrefsWriter,
) : PreferencesRepository {
    // Toute écriture repasse par cette émission (observée en continu dès
    // Application.onCreate) : un seul point d'alimentation du cache de démarrage.
    override fun observe(): Flow<UserPreferences> = dao.observe()
        .map { it?.toDomain() ?: UserPreferences() }
        .onEach(startupCache::write)

    override suspend fun get(): UserPreferences = dao.get()?.toDomain() ?: UserPreferences()
    override suspend fun update(preferences: UserPreferences) = dao.upsert(preferences.toEntity())

    private val updateMutex = Mutex()

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) = updateMutex.withLock {
        dao.upsert(transform(get()).toEntity())
    }
}
