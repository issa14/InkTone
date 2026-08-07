package com.inktone.infrastructure.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.impl.WorkManagerImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkManagerImportSchedulerTest {

    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun enqueue_avec_peu_d_uri_cree_un_seul_travail() {
        val scheduler = WorkManagerImportScheduler(workManager)

        scheduler.enqueue(listOf("content://fake/1.epub", "content://fake/2.epub"))

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerImportScheduler.WORK_NAME_IMPORT).get()
        assertEquals(1, infos.size)
        assertTrue(infos.first().state == WorkInfo.State.ENQUEUED)
    }

    @Test
    fun enqueue_avec_plus_de_50_uri_chaine_plusieurs_travaux() {
        val scheduler = WorkManagerImportScheduler(workManager)
        val uris = (1..120).map { "content://fake/$it.epub" }

        scheduler.enqueue(uris)

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerImportScheduler.WORK_NAME_IMPORT).get()
        // 120 URI / 50 par lot = 3 WorkRequest chainees.
        assertEquals(3, infos.size)
    }

    @Test
    fun enqueue_de_60_uri_partage_le_meme_session_id_sur_toute_la_chaine() {
        val scheduler = WorkManagerImportScheduler(workManager)
        val uris = (1..60).map { "content://fake/$it.epub" }

        val sessionId = scheduler.enqueue(uris)

        // La session est generee et retournee a l'appelant
        assertTrue(sessionId.isNotEmpty())

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerImportScheduler.WORK_NAME_IMPORT).get()
        // 60 / 50 = 2 WorkRequest chainees.
        assertEquals(2, infos.size)

        // Chaque maillon porte le meme sessionId dans sa Data d'entree :
        // c'est ce qui permet d'agreger les resultats sur toute la chaine
        // (Tache 5.2, Lot 5).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDatabase = WorkManagerImpl.getInstance(context).workDatabase
        infos.forEach { info ->
            val spec = workDatabase.workSpecDao().getWorkSpec(info.id.toString())
            assertEquals(sessionId, spec!!.input.getString(ImportWorker.KEY_SESSION_ID))
        }
    }

    @Test
    fun aucune_uri_n_enqueue_rien() {
        val scheduler = WorkManagerImportScheduler(workManager)

        scheduler.enqueue(emptyList())

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerImportScheduler.WORK_NAME_IMPORT).get()
        assertTrue(infos.isEmpty())
    }
}
