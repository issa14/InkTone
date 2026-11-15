package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakeChapterParser
import com.inktone.core.testing.fake.FakeFileStorageService
import com.inktone.core.testing.fake.FakePlaybackSession
import com.inktone.core.testing.fake.FakePreAnalysisStore
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeRenderedPageCache
import com.inktone.core.testing.fake.FakeSearchService
import com.inktone.core.testing.fake.FakeTtsSegmentCache
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR-028, audit UX D55 et D63 : un livre vit dans le stockage de
 * l'application, du premier import à sa suppression.
 */
class LibraryPrivateCopyTest {

    private val repository = FakePublicationRepository()
    private val storage = FakeFileStorageService()
    private val parser = FakePublicationParser()

    private fun importPublication() = ImportPublicationUseCase(
        publicationParser = parser,
        publicationRepository = repository,
        fileStorageService = storage,
        searchService = FakeSearchService(),
        chapterParser = FakeChapterParser(),
        preAnalysisStore = FakePreAnalysisStore(),
    )

    @Test
    fun `un livre importe pointe sur sa copie privee, pas sur le fichier choisi`() = runTest {
        val result = importPublication()("content://picker/livre.epub")

        val publication = (result as ImportResult.Success).publication
        assertTrue(publication.fileUri.startsWith(FakeFileStorageService.LIBRARY_PREFIX))
        assertTrue(publication.fileUri.endsWith(".epub"))
        assertEquals(publication.fileUri, repository.getById(publication.id)?.fileUri)
    }

    @Test
    fun `un telechargement OPDS, deja dans l'application, n'est pas recopie`() = runTest {
        val opds = "${FakeFileStorageService.APP_PREFIX}opds_downloads/livre.epub"

        val result = importPublication()(opds)

        assertEquals(opds, (result as ImportResult.Success).publication.fileUri)
    }

    @Test
    fun `une copie impossible fait echouer l'import sans creer de livre`() = runTest {
        storage.failCopies = true

        val result = importPublication()("content://picker/livre.epub")

        assertTrue(result is ImportResult.Corrupted)
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `la copie d'un import qui echoue est retiree`() = runTest {
        parser.setNextResult(ParseResult.Corrupted("illisible"))

        importPublication()("content://picker/livre.epub")

        assertEquals(1, storage.deletedUris.size)
        assertTrue(storage.deletedUris.single().startsWith(FakeFileStorageService.LIBRARY_PREFIX))
    }

    @Test
    fun `supprimer un livre supprime son fichier quand il appartient a l'application`() = runTest {
        val publication = (importPublication()("content://picker/livre.epub") as ImportResult.Success).publication
        val delete = DeletePublicationUseCase(
            repository, FakePreAnalysisStore(), FakeTtsSegmentCache(), FakeRenderedPageCache(),
            FakePlaybackSession(), FakeSearchService(), storage,
        )

        delete(publication.id)

        assertEquals(listOf(publication.fileUri), storage.deletedUris)
    }

    @Test
    fun `le rattrapage copie les anciens imports et garde ceux deja copies`() = runTest {
        val ancien = publication("ancien", fileUri = "content://picker/ancien.pdf", format = PublicationFormat.PDF)
        val copie = publication("copie", fileUri = "${FakeFileStorageService.LIBRARY_PREFIX}h.epub")
        repository.insert(ancien)
        repository.insert(copie)

        val outcome = CopyLibraryToPrivateStorageUseCase(repository, storage)()

        assertEquals(CopyLibraryToPrivateStorageUseCase.Outcome(copied = 1, failed = 0), outcome)
        assertEquals("${FakeFileStorageService.LIBRARY_PREFIX}hash-ancien.pdf", repository.getById("ancien")?.fileUri)
        assertEquals(copie.fileUri, repository.getById("copie")?.fileUri)
    }

    @Test
    fun `un rattrapage impossible laisse le livre sur son fichier d'origine`() = runTest {
        val ancien = publication("ancien", fileUri = "content://picker/ancien.epub")
        repository.insert(ancien)
        storage.failCopies = true

        val outcome = CopyLibraryToPrivateStorageUseCase(repository, storage)()

        assertEquals(1, outcome.failed)
        assertEquals(ancien.fileUri, repository.getById("ancien")?.fileUri)
    }

    private fun publication(id: String, fileUri: String, format: PublicationFormat = PublicationFormat.EPUB) = Publication(
        id = id, title = "Titre $id", format = format, fileUri = fileUri, fileHash = "hash-$id",
        fileSize = 10L, chapterCount = 1, importDate = 0L,
    )

    /** Audit UX D68 : les fichiers de couverture n'étaient pas supprimés avec le livre. */
    @Test
    fun `supprimer un livre supprime sa couverture, sauf si un autre livre la partage`() = runTest {
        val cover = "${FakeFileStorageService.APP_PREFIX}covers/123.jpg"
        repository.insert(publication("seul", fileUri = "content://picker/a.epub").copy(coverUri = cover))
        repository.insert(publication("partage-1", fileUri = "content://picker/b.epub").copy(coverUri = "${FakeFileStorageService.APP_PREFIX}covers/9.jpg"))
        repository.insert(publication("partage-2", fileUri = "content://picker/c.epub").copy(coverUri = "${FakeFileStorageService.APP_PREFIX}covers/9.jpg"))
        val delete = DeletePublicationUseCase(
            repository, FakePreAnalysisStore(), FakeTtsSegmentCache(), FakeRenderedPageCache(),
            FakePlaybackSession(), FakeSearchService(), storage,
        )

        delete("seul")
        delete("partage-1")

        assertEquals(listOf(cover), storage.deletedUris)
    }
}
