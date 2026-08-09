package com.inktone.infrastructure.worker

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.inktone.domain.service.SyncScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémente [SyncScheduler] via WorkManager (tâche 11.8) — nom de
 * travail unique [WORK_NAME_AUTO_SYNC], **distinct** de
 * [WorkManagerImportScheduler.WORK_NAME_IMPORT] : les deux planifications
 * ne doivent jamais se percuter.
 *
 * 15 minutes est le plus petit intervalle que `PeriodicWorkRequest`
 * accepte (contrainte WorkManager, pas un choix produit) — ce n'est pas
 * un signal de présence : chaque déclenchement tente une vraie
 * synchronisation ([AutoSyncWorker]), rien n'est écrit à distance si
 * elle ne se produit pas.
 */
@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) : SyncScheduler {

    override fun schedule(wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<AutoSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        // UPDATE (pas CANCEL_AND_REENQUEUE) : un changement de "Wi-Fi
        // uniquement" doit s'appliquer sans perdre la planification en
        // cours ni redémarrer la période artificiellement.
        workManager.enqueueUniquePeriodicWork(WORK_NAME_AUTO_SYNC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME_AUTO_SYNC)
    }

    companion object {
        const val WORK_NAME_AUTO_SYNC = "inktone_auto_sync"
    }
}
