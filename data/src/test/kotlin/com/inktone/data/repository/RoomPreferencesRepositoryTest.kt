package com.inktone.data.repository

import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.UserPreferences
import com.inktone.infrastructure.database.dao.UserPreferencesDao
import com.inktone.infrastructure.database.entity.UserPreferencesEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Audit UX D67 : « lire toutes les préférences, puis réécrire toute la ligne »
 * dans des coroutines indépendantes laissait deux écritures s'écraser.
 */
class RoomPreferencesRepositoryTest {

    /** DAO dont la lecture rend la main : élargit la fenêtre entre lecture et écriture. */
    private class SlowDao(initial: UserPreferencesEntity) : UserPreferencesDao {
        private val row = MutableStateFlow<UserPreferencesEntity?>(initial)
        override fun observe(): Flow<UserPreferencesEntity?> = row
        override suspend fun get(): UserPreferencesEntity? {
            val read = row.value
            delay(5)
            return read
        }
        override suspend fun upsert(entity: UserPreferencesEntity) {
            row.value = entity
        }
    }

    @Test
    fun `des mises a jour concurrentes de champs differents se cumulent toutes`() = runBlocking {
        val repository = RoomPreferencesRepository(
            SlowDao(UserPreferences(fontSize = 10, autoScrollSpeed = 0).toEntity()),
            startupCache = object : StartupPrefsWriter { override fun write(preferences: UserPreferences) = Unit },
        )

        (0 until 20).map { i ->
            async(Dispatchers.Default) {
                if (i % 2 == 0) repository.update { it.copy(fontSize = it.fontSize + 1) }
                else repository.update { it.copy(autoScrollSpeed = (it.autoScrollSpeed + 1).coerceAtMost(3)) }
            }
        }.awaitAll()

        val result = repository.get()
        assertEquals("aucune des 10 augmentations de taille n'est perdue", 20, result.fontSize)
        assertEquals("la vitesse a bien été modifiée en parallèle", 3, result.autoScrollSpeed)
    }
}
