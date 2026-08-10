package com.inktone.infrastructure.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Lot 11, tâche 11.9 — nom de travail distinct de l'import, contrainte réseau reflétant "Wi-Fi uniquement", un changement de contrainte remplace la planification sans la dupliquer. */
@RunWith(AndroidJUnit4::class)
class WorkManagerSyncSchedulerTest {

    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun schedule_enfile_un_seul_travail_periodique_nomme() {
        val scheduler = WorkManagerSyncScheduler(workManager)

        scheduler.schedule(wifiOnly = false)

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.WORK_NAME_AUTO_SYNC).get()
        assertEquals(1, infos.size)
        assertTrue(infos.first().state == WorkInfo.State.ENQUEUED)
        assertTrue(WorkManagerSyncScheduler.WORK_NAME_AUTO_SYNC != WorkManagerImportScheduler.WORK_NAME_IMPORT)
    }

    @Test
    fun schedule_avec_wifiOnly_pose_la_contrainte_reseau_UNMETERED() {
        val scheduler = WorkManagerSyncScheduler(workManager)

        scheduler.schedule(wifiOnly = true)

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.WORK_NAME_AUTO_SYNC).get()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val spec = WorkManagerImpl.getInstance(context).workDatabase.workSpecDao().getWorkSpec(infos.first().id.toString())
        assertEquals(NetworkType.UNMETERED, spec!!.constraints.requiredNetworkType)
    }

    @Test
    fun schedule_sans_wifiOnly_pose_la_contrainte_reseau_CONNECTED() {
        val scheduler = WorkManagerSyncScheduler(workManager)

        scheduler.schedule(wifiOnly = false)

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.WORK_NAME_AUTO_SYNC).get()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val spec = WorkManagerImpl.getInstance(context).workDatabase.workSpecDao().getWorkSpec(infos.first().id.toString())
        assertEquals(NetworkType.CONNECTED, spec!!.constraints.requiredNetworkType)
    }

    @Test
    fun schedule_repete_ne_duplique_pas_le_travail_unique() {
        val scheduler = WorkManagerSyncScheduler(workManager)

        scheduler.schedule(wifiOnly = false)
        scheduler.schedule(wifiOnly = true)

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.WORK_NAME_AUTO_SYNC).get()
        assertEquals(1, infos.size)
    }

    @Test
    fun cancel_annule_le_travail_planifie() {
        val scheduler = WorkManagerSyncScheduler(workManager)
        scheduler.schedule(wifiOnly = false)

        scheduler.cancel()

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.WORK_NAME_AUTO_SYNC).get()
        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
    }
}
