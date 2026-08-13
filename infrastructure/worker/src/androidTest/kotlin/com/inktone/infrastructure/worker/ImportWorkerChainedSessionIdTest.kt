package com.inktone.infrastructure.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.inktone.core.testing.fake.FakeChapterParser
import com.inktone.core.testing.fake.FakeFileStorageService
import com.inktone.core.testing.fake.FakeImportResultsStore
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeSearchService
import com.inktone.domain.usecase.ImportPublicationUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Régression device (Lot 5) : `WorkManagerImportScheduler` chaîne tout
 * import successif sur le même nom de travail unique (`APPEND_OR_REPLACE`).
 * Reproduit ici avec un vrai `WorkManager` (pas `TestListenableWorkerBuilder`,
 * qui construit un worker isolé et ne passe jamais par la chaîne réelle) :
 * deux imports enchaînés doivent persister leurs résultats chacun sous
 * LEUR PROPRE sessionId, jamais celui hérité du maillon précédent via
 * l'`InputMerger` par défaut de WorkManager.
 */
@RunWith(AndroidJUnit4::class)
class ImportWorkerChainedSessionIdTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var importResultsStore: FakeImportResultsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        importResultsStore = FakeImportResultsStore()

        val importPublication = ImportPublicationUseCase(
            publicationParser = FakePublicationParser(),
            publicationRepository = FakePublicationRepository(),
            fileStorageService = FakeFileStorageService(),
            searchService = FakeSearchService(),
            chapterParser = FakeChapterParser(),
        )

        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = ImportWorker(appContext, workerParameters, importPublication, importResultsStore)
        }

        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(workerFactory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    /**
     * `setAllConstraintsMet` ne fait que lever la contrainte : le worker
     * s'exécute ensuite de façon asynchrone via son propre dispatcher de
     * coroutine (le `SynchronousExecutor` de WorkManager ne couvre pas
     * ça) — on attend donc activement l'état terminal plutôt que de le
     * lire immédiatement après l'appel.
     */
    private fun awaitTerminalState(id: java.util.UUID, timeoutMs: Long = 5000): WorkInfo.State {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val state = workManager.getWorkInfoById(id).get()!!.state
            if (state.isFinished) return state
            Thread.sleep(20)
        }
        throw AssertionError("Le travail $id n'a pas atteint un etat terminal sous ${timeoutMs}ms")
    }

    @Test
    fun deux_imports_enchaines_persistent_chacun_sous_leur_propre_session_id() {
        val scheduler = WorkManagerImportScheduler(workManager)
        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)!!

        val firstSessionId = scheduler.enqueue(listOf("content://fake/a.epub"))
        var infos = workManager.getWorkInfosForUniqueWork(WorkManagerImportScheduler.WORK_NAME_IMPORT).get()
        val firstId = infos.first().id
        testDriver.setAllConstraintsMet(firstId)
        assertEquals(WorkInfo.State.SUCCEEDED, awaitTerminalState(firstId))

        val secondSessionId = scheduler.enqueue(listOf("content://fake/b.epub"))
        assertNotEquals("deux enqueue() generent des sessionId distincts", firstSessionId, secondSessionId)

        infos = workManager.getWorkInfosForUniqueWork(WorkManagerImportScheduler.WORK_NAME_IMPORT).get()
        val secondWork = infos.first { it.id != firstId }
        testDriver.setAllConstraintsMet(secondWork.id)
        assertEquals(WorkInfo.State.SUCCEEDED, awaitTerminalState(secondWork.id))

        // Le résultat de "b.epub" doit être enregistré sous secondSessionId,
        // jamais sous firstSessionId hérité du maillon précédent via
        // l'InputMerger (le bug reproduit sur appareil).
        val recordedForB = importResultsStore.recordedSessionIds.last()
        assertEquals(secondSessionId, recordedForB)
        assertTrue(
            "aucun resultat ne doit rester attribue au premier sessionId apres le second import",
            importResultsStore.entries.none { it.fileName == "b.epub" } || recordedForB != firstSessionId,
        )
    }
}
