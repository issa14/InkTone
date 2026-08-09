package com.inktone.domain.repository

import com.inktone.domain.model.SyncActivityEvent

/**
 * Journal d'activité distant (tâche 11.8) — fichier plafonné, 5 à 10
 * derniers événements seulement (voir l'implémentation pour la valeur
 * exacte). Même discipline que [SyncFleetRepository] : [appendEvent]
 * relit l'existant avant d'écrire.
 */
interface SyncActivityLogRepository {
    /** Les plus récents en premier. */
    suspend fun listEvents(): List<SyncActivityEvent>

    /** Relit le journal, ajoute [event], plafonne, réécrit. */
    suspend fun appendEvent(event: SyncActivityEvent)
}
