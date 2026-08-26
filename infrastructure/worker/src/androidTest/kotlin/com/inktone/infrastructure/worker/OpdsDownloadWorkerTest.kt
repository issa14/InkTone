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
import com.inktone.core.testing.fake.FakeOpdsDownloadObserver
import com.inktone.core.testing.fake.FakeOpdsHttpClient
import com.inktone.core.testing.fake.FakePreAnalysisStore
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeSearchService
import com.inktone.domain.service.OpdsDownloadObserver
import com.inktone.domain.service.OpdsDownloadResult
import com.inktone.domain.service.OpdsFailureReason
import com.inktone.domain.service.OpdsHttpClient
import com.inktone.domain.usecase.ImportPublicationUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Lot 13, tâche 13.3.5 — succès et échec réseau du téléchargement OPDS (le pipeline d'import réel, pas mocké). */
@RunWith(AndroidJUnit4::class)
class OpdsDownloadWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun workerFactory(
        httpClient: OpdsHttpClient,
        importPublication: ImportPublicationUseCase,
        observer: OpdsDownloadObserver,
    ) = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = OpdsDownloadWorker(appContext, workerParameters, httpClient, importPublication, observer)
    }

    private fun importUseCase() = ImportPublicationUseCase(
        publicationParser = FakePublicationParser(),
        publicationRepository = FakePublicationRepository(),
        fileStorageService = FakeFileStorageService(),
        searchService = FakeSearchService(),
        chapterParser = FakeChapterParser(),
        preAnalysisStore = FakePreAnalysisStore(),
    )

    private fun buildWorker(
        httpClient: OpdsHttpClient,
        importPublication: ImportPublicationUseCase,
        observer: OpdsDownloadObserver,
    ) = TestListenableWorkerBuilder<OpdsDownloadWorker>(context)
        .setWorkerFactory(workerFactory(httpClient, importPublication, observer))
        .setInputData(
            workDataOf(
                OpdsDownloadWorker.KEY_ACQUISITION_HREF to "https://ex.com/book.epub",
                OpdsDownloadWorker.KEY_CATALOG_ID to "cat-1",
                OpdsDownloadWorker.KEY_BOOK_TITLE to "Le Livre",
            ),
        )
        .build()

    @Test
    fun telechargement_et_import_reussis_publient_le_livre_importe() = runTest {
        val observer = FakeOpdsDownloadObserver()
        val worker = buildWorker(
            httpClient = FakeOpdsHttpClient(
                onFetch = { _, _ -> com.inktone.domain.service.OpdsFetchResult.Success("<feed/>", "https://ex.com/book.epub") },
            ).apply {
                // download renvoie des octets
                onDownload = { _, _ -> OpdsDownloadResult.Success(byteArrayOf(1, 2, 3)) }
            },
            importPublication = importUseCase(),
            observer = observer,
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, observer.published.size)
        assertTrue(observer.published.first().success)
        assertEquals("Le Livre", observer.published.first().bookTitle)
        // publicationId non nul : le pipeline d'import réel a inséré une publication.
        assertTrue(observer.published.first().publicationId != null)
    }

    @Test
    fun un_echec_reseau_publie_un_evenement_d_echec_et_ne_importe_rien() = runTest {
        val observer = FakeOpdsDownloadObserver()
        val worker = buildWorker(
            httpClient = FakeOpdsHttpClient().apply {
                onDownload = { _, _ -> OpdsDownloadResult.Failure(OpdsFailureReason.NETWORK, "Hors ligne") }
            },
            importPublication = importUseCase(),
            observer = observer,
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(1, observer.published.size)
        assertEquals(false, observer.published.first().success)
        assertNull(observer.published.first().publicationId)
    }
}
