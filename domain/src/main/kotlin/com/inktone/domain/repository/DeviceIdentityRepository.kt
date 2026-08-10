package com.inktone.domain.repository

import com.inktone.domain.model.DeviceIdentity

/**
 * Identité stable de l'appareil courant (tâche 11.2). `getOrCreate` est
 * idempotent : génère l'identifiant et un nom lisible au tout premier
 * appel, les persiste, puis rend toujours la même valeur — l'implémentation
 * (`data`) est seule responsable de la stabilité entre deux lancements.
 */
interface DeviceIdentityRepository {
    suspend fun getOrCreate(): DeviceIdentity
}
