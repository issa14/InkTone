package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakeFileStorageService
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportLibraryUseCaseTest {

    @Test
    fun `exporte chaque publication en une entree distincte du zip`() = runTest {
        val repository = FakePublicationRepository()
        repository.insert(
            Publication(
                id = "pub-1", title = "Les Misérables", format = PublicationFormat.EPUB,
                fileUri = "content://fake/1", fileHash = "hash1", fileSize = 1000L,
                chapterCount = 10, importDate = 0L,
            ),
        )
        repository.insert(
            Publication(
                id = "pub-2", title = "Notre-Dame de Paris", format = PublicationFormat.EPUB,
                fileUri = "content://fake/2", fileHash = "hash2", fileSize = 2000L,
                chapterCount = 5, importDate = 0L,
            ),
        )
        val fileStorageService = FakeFileStorageService()
        val exportLibrary = ExportLibraryUseCase(repository, fileStorageService)

        val result = exportLibrary("content://fake/destination.zip")

        check(result is ExportResult.Success)
        assertEquals(2, result.exportedCount)
        assertEquals(listOf("content://fake/destination.zip"), fileStorageService.writtenUris)
    }

    @Test
    fun `bibliotheque vide retourne un echec explicite`() = runTest {
        val exportLibrary = ExportLibraryUseCase(FakePublicationRepository(), FakeFileStorageService())

        val result = exportLibrary("content://fake/destination.zip")

        assertTrue(result is ExportResult.Failure)
    }
}
