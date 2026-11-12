package com.inktone.domain.service

import kotlinx.coroutines.flow.Flow

/**
 * Téléchargement de la voix neuronale en tâche de fond, indépendant de tout
 * écran (audit UX A7) : lancé depuis les Réglages, il continue quand on les
 * quitte, et sa progression se suit depuis n'importe quel écran.
 */
interface VoiceModelDownloadScheduler {

    /** Lance le téléchargement ; sans effet s'il est déjà en cours. */
    fun start()

    /** Annule le téléchargement en cours. */
    fun cancel()

    /** Progression du dernier téléchargement lancé, `null` si aucun n'est en cours ni n'a abouti. */
    val progress: Flow<VoiceDownloadProgress?>
}
