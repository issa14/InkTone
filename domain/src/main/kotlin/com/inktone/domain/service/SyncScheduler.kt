package com.inktone.domain.service

/**
 * Planification de la synchro automatique en arrière-plan (tâche 11.8) —
 * même discipline que [ImportScheduler] : aucun module feature ne connaît
 * WorkManager directement, l'implémentation vit dans `infrastructure`.
 * [schedule] et [cancel] sont idempotents.
 */
interface SyncScheduler {
    fun schedule(wifiOnly: Boolean)
    fun cancel()
}
