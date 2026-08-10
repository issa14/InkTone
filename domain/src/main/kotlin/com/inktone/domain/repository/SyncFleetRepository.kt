package com.inktone.domain.repository

import com.inktone.domain.model.DeviceFleetEntry

/**
 * Registre distant des appareils liés (tâche 11.8, fichier dans le
 * dossier applicatif du fournisseur actif — pas de serveur).
 *
 * **Aucun verrou distant disponible** : [touchCurrentDevice] doit relire
 * l'intégralité du registre puis ne remplacer que l'entrée de l'appareil
 * courant avant de réécrire le tout — jamais écraser aveuglément
 * l'ensemble, sous peine de faire disparaître les autres appareils d'une
 * flotte synchronisée au même instant.
 */
interface SyncFleetRepository {
    suspend fun listDevices(): List<DeviceFleetEntry>

    /** Relit le registre, remplace uniquement l'entrée dont `deviceId` correspond à [entry], réécrit l'ensemble. */
    suspend fun touchCurrentDevice(entry: DeviceFleetEntry)

    /**
     * Retire [deviceId] de la liste — un **nettoyage de liste**, pas une
     * révocation : l'appareil retiré détient toujours les identifiants et
     * réapparaîtra à sa prochaine synchronisation réelle. Ne jamais
     * présenter cette action comme une mesure de sécurité côté UI.
     */
    suspend fun removeDevice(deviceId: String)
}
