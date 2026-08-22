package com.inktone.domain.model

/**
 * Une entrée de la flotte d'appareils liés au compte de synchronisation
 * (tâche 11.8) — fichier de registre distant, pas de serveur. `deviceType`
 * reste une chaîne libre plutôt qu'un enum fermé : seul "Android" existe
 * aujourd'hui, mais rien n'empêche un futur client d'un autre type
 * d'écrire sa propre valeur sans recompiler cette app.
 */
data class DeviceFleetEntry(
    val deviceId: String,
    val displayName: String,
    val deviceType: String = "Android",
    val lastActiveAt: Long,
) {
    init {
        require(deviceId.isNotBlank()) { "deviceId ne peut pas être vide" }
        require(displayName.isNotBlank()) { "displayName ne peut pas être vide" }
    }
}

/**
 * Type d'événement du journal d'activité (tâche 11.8) — chacun porte une
 * icône de **forme** distincte côté UI (`AppSymbol`), la couleur ne vient
 * qu'en renfort (accessibilité daltonisme/TalkBack, jamais seule).
 */
enum class SyncActivityEventType { SUCCESS, NETWORK_FAILURE, MANUAL_SYNC }

data class SyncActivityEvent(
    val id: String,
    val type: SyncActivityEventType,
    val message: String,
    val occurredAt: Long,
)
