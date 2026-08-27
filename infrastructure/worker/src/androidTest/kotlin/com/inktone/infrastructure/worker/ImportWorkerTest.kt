package com.inktone.infrastructure.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.inktone.core.testing.fake.FakeChapterParser
import com.inktone.core.testing.fake.FakeFileStorageService
import com.inktone.core.testing.fake.FakeImportResultsStore
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeSearchService
import com.inktone.domain.service.ImportResultsStore
import com.inktone.domain.usecase.ImportPublicationUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `ImportWorker` étant `@HiltWorker`, sa construction directe (constructeur
 * `@AssistedInject`) reste appelable hors du graphe Hilt via une
 * `WorkerFactory` de test — pas besoin d'assembler tout le composant Hilt
 * pour ce test (mêmes fakes que `ImportPublicationUseCaseTest`, Tâche 6.4).
 */
@RunWith(AndroidJUnit4::class)
class ImportWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun workerFactory(
        importPublication: ImportPublicationUseCase,
        importResultsStore: ImportResultsStore,
    ) = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = ImportWorker(appContext, workerParameters, importPublication, importResultsStore)
    }

    @Test
    fun import_de_plusieurs_uris_agrege_succes_et_doublons() = runTest {
        val publicationRepository = FakePublicationRepository()
        val importPublication = ImportPublicationUseCase(
            publicationParser = FakePublicationParser(),
            publicationRepository = publicationRepository,
            fileStorageService = FakeFileStorageService(),
            searchService = FakeSearchService(),
            chapterParser = FakeChapterParser(),
        )

        val worker = TestListenableWorkerBuilder<ImportWorker>(context)
            .setWorkerFactory(workerFactory(importPublication, FakeImportResultsStore()))
            .setInputData(
                workDataOf(
                    ImportWorker.KEY_URIS to arrayOf(
                        "content://fake/1.epub",
                        "content://fake/2.epub",
                        "content://fake/1.epub", // doublon volontaire
                    ),
                ),
            )
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertEquals(2, output.getInt(ImportWorker.KEY_RESULT_SUCCESS, -1))
        assertEquals(1, output.getInt(ImportWorker.KEY_RESULT_DUPLICATE, -1))
        assertEquals(0, output.getInt(ImportWorker.KEY_RESULT_FAILURE, -1))
    }

    @Test
    fun persiste_un_resultat_par_fichier_avec_une_session_unique() = runTest {
        val importResultsStore = FakeImportResultsStore()
        val publicationRepository = FakePublicationRepository()
        val importPublication = ImportPublicationUseCase(
            publicationParser = FakePublicationParser(),
            publicationRepository = publicationRepository,
            fileStorageService = FakeFileStorageService(),
            searchService = FakeSearchService(),
            chapterParser = FakeChapterParser(),
        )

        val worker = TestListenableWorkerBuilder<ImportWorker>(context)
            .setWorkerFactory(workerFactory(importPublication, importResultsStore))
            .setInputData(
                workDataOf(
                    ImportWorker.KEY_URIS to arrayOf("content://fake/1.epub"),
                    ImportWorker.KEY_SESSION_ID to "session-42",
                ),
            )
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, importResultsStore.entries.size)
        assertEquals("1.epub", importResultsStore.entries.first().fileName)
        assertEquals("success", importResultsStore.entries.first().resultType)
        assertEquals("session-42", importResultsStore.recordedSessionIds.firstOrNull())
    }

    @Test
    fun sans_uris_en_entree_echoue_immediatement() = runTest {
        val importPublication = ImportPublicationUseCase(
            publicationParser = FakePublicationParser(),
            publicationRepository = FakePublicationRepository(),
            fileStorageService = FakeFileStorageService(),
            searchService = FakeSearchService(),
            chapterParser = FakeChapterParser(),
        )

        val worker = TestListenableWorkerBuilder<ImportWorker>(context)
            .setWorkerFactory(workerFactory(importPublication, FakeImportResultsStore()))
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }
}
