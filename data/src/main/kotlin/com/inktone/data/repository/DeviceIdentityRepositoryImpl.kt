package com.inktone.data.repository

import android.os.Build
import com.inktone.domain.model.DeviceIdentity
import com.inktone.domain.repository.DeviceIdentityRepository
import com.inktone.domain.repository.PreferencesRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Génère l'identité au tout premier appel puis la persiste (tâche 11.2)
 * — `UserPreferences.deviceId` reste `null` jusque-là (installation
 * neuve, même raisonnement que `hasSeenOnboarding`). Le nom lisible par
 * défaut vient de `Build.MODEL` (ex. « Pixel 7 ») : une valeur de
 * démarrage que l'utilisateur pourra renommer depuis l'écran
 * Opérationnel (palier C), jamais figée en dur ici.
 */
class DeviceIdentityRepositoryImpl @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : DeviceIdentityRepository {

    override suspend fun getOrCreate(): DeviceIdentity {
        val current = preferencesRepository.get()
        val existingId = current.deviceId
        val existingName = current.deviceDisplayName
        if (existingId != null && existingName != null) {
            return DeviceIdentity(existingId, existingName)
        }

        val id = existingId ?: UUID.randomUUID().toString()
        val name = existingName ?: defaultDisplayName()
        preferencesRepository.update(current.copy(deviceId = id, deviceDisplayName = name))
        return DeviceIdentity(id, name)
    }

    private fun defaultDisplayName(): String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Appareil inconnu"
}
