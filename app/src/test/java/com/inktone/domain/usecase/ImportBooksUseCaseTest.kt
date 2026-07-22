package com.inktone.domain.usecase

import android.content.Context
import android.net.Uri
import com.inktone.domain.model.Book
import com.inktone.domain.repository.BookRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Tests unitaires pour [ImportBooksUseCase] — extrait de `LibraryViewModel.importBooks()`
 * (voir PLAN import EPUB §4) pour être réutilisable par `EpubImportWorker`. Couvre les mêmes
 * garanties que l'ancien test `LibraryViewModelTest` sur `importBooks()`.
 */
class ImportBooksUseCaseTest {

    private val bookRepository = mockk<BookRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val useCase = ImportBooksUseCase(context, bookRepository)

    private val baseBook = Book(
        id = "unused", title = "T", author = "A", description = null,
        totalChapters = 1, language = "fr", addedAt = 0L
    )

    /**
     * Un livre bloqué en cours d'import doit rester dans `importingBookIds` de la progression
     * tant qu'il n'est pas terminé, et un livre déjà fini ne doit plus y figurer — les deux
     * imports sont distingués par l'identité du flux retourné par `openInputStream()` (pas par
     * l'ordre d'exécution des coroutines, non déterministe).
     */
    @Test
    fun `la progression reflete les livres encore en cours, pas ceux deja termines`() = runBlocking {
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()
        val stream1 = ByteArrayInputStream(ByteArray(0))
        val stream2 = ByteArrayInputStream(ByteArray(0))
        val gateForBook2 = CompletableDeferred<Unit>()

        val contentResolver = context.contentResolver
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns null
        every { contentResolver.openInputStream(uri1) } returns stream1
        every { contentResolver.openInputStream(uri2) } returns stream2

        coEvery { bookRepository.importEpub(any(), any(), any(), any(), any()) } coAnswers {
            val stream = secondArg<InputStream>()
            if (stream === stream1) {
                baseBook.copy(id = "import-1")
            } else {
                gateForBook2.await()
                baseBook.copy(id = "import-2")
            }
        }

        val progressUpdates = mutableListOf<ImportBooksUseCase.Progress>()
        val job = launch {
            useCase(listOf(uri1, uri2)) { progress -> progressUpdates.add(progress) }
        }

        withTimeout(5_000) {
            while (progressUpdates.none { it.completed == 1 }) yield()
        }
        val afterFirstCompletion = progressUpdates.last()
        assertEquals(1, afterFirstCompletion.completed)
        assertTrue(
            afterFirstCompletion.importingBookIds.size == 1,
            "Le 2e livre doit rester dans importingBookIds tant qu'il n'est pas terminé"
        )

        gateForBook2.complete(Unit)
        job.join()

        val final = progressUpdates.last()
        assertEquals(2, final.completed)
        assertTrue(final.importingBookIds.isEmpty(), "Plus aucun livre en cours une fois le lot terminé")
    }

    @Test
    fun `un echec individuel n'interrompt pas les autres imports et est retourne`() = runBlocking {
        val uriOk = mockk<Uri>()
        val uriFail = mockk<Uri>()
        val streamOk = ByteArrayInputStream(ByteArray(0))
        val streamFail = ByteArrayInputStream(ByteArray(0))

        val contentResolver = context.contentResolver
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns null
        every { contentResolver.openInputStream(uriOk) } returns streamOk
        every { contentResolver.openInputStream(uriFail) } returns streamFail

        coEvery { bookRepository.importEpub(any(), any(), any(), any(), any()) } coAnswers {
            val stream = secondArg<InputStream>()
            if (stream === streamOk) baseBook.copy(id = "ok") else throw IllegalStateException("EPUB corrompu")
        }

        val failed = useCase(listOf(uriOk, uriFail)) { }

        assertEquals(1, failed.size)
    }
}
