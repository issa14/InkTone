package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakeOpdsDownloadScheduler
import com.inktone.domain.model.OpdsItem
import com.inktone.domain.service.OpdsFailureReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lot 13, tâche 13.3.5 — acquisition non-EPUB rejetée avant tout appel réseau/scheduler. */
class DownloadOpdsBookUseCaseTest {

    private fun book(mimeType: String, acquisitionHref: String = "https://ex.com/book.epub") = OpdsItem.Book(
        title = "Titre",
        authors = emptyList(),
        coverUrl = null,
        acquisitionHref = acquisitionHref,
        mimeType = mimeType,
    )

    @Test
    fun un_livre_epub_direct_est_planifie() = runTest {
        val scheduler = FakeOpdsDownloadScheduler()
        val useCase = DownloadOpdsBookUseCase(scheduler)

        val result = useCase(book("application/epub+zip"), "cat-1")

        assertTrue(result is DownloadOpdsBookResult.Scheduled)
        assertEquals(1, scheduler.enqueued.size)
        assertEquals("https://ex.com/book.epub", scheduler.enqueued.first().first)
        assertEquals("cat-1", scheduler.enqueued.first().second)
    }

    @Test
    fun un_mime_non_epub_est_rejete_sans_planifier() = runTest {
        val scheduler = FakeOpdsDownloadScheduler()
        val useCase = DownloadOpdsBookUseCase(scheduler)

        val result = useCase(book("application/pdf"), "cat-1")

        assertTrue(result is DownloadOpdsBookResult.Failure)
        assertEquals(OpdsFailureReason.NON_DOWNLOADABLE_ACQUISITION, (result as DownloadOpdsBookResult.Failure).reason)
        assertEquals(0, scheduler.enqueued.size)
    }

    @Test
    fun un_lien_d_acquisition_vide_est_rejete_sans_planifier() = runTest {
        val scheduler = FakeOpdsDownloadScheduler()
        val useCase = DownloadOpdsBookUseCase(scheduler)

        val result = useCase(book("application/epub+zip", acquisitionHref = ""), null)

        assertTrue(result is DownloadOpdsBookResult.Failure)
        assertEquals(OpdsFailureReason.NON_DOWNLOADABLE_ACQUISITION, (result as DownloadOpdsBookResult.Failure).reason)
        assertEquals(0, scheduler.enqueued.size)
    }

    @Test
    fun un_mime_absent_est_accepte_par_defaut() = runTest {
        val scheduler = FakeOpdsDownloadScheduler()
        val useCase = DownloadOpdsBookUseCase(scheduler)

        val result = useCase(book(""), "cat-1")

        assertTrue(result is DownloadOpdsBookResult.Scheduled)
        assertEquals(1, scheduler.enqueued.size)
    }
}
