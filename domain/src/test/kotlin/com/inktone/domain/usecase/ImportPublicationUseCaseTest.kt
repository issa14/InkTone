package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakeFileStorageService
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportPublicationUseCaseTest {

    @Test
    fun `importer deux fois le meme fichier ne cree pas de doublon`() = runTest {
        val publicationRepository = FakePublicationRepository()
        val importPublication = ImportPublicationUseCase(
            publicationParser = FakePublicationParser(),
            publicationRepository = publicationRepository,
            fileStorageService = FakeFileStorageService(),
        )
        val fixtureUri = "content://fake/fixture-minimal.epub"

        val first = importPublication(fixtureUri)
        val second = importPublication(fixtureUri)

        check(first is ImportResult.Success)
        check(second is ImportResult.Duplicate)
        assertEquals(first.publication.id, second.existingPublicationId)

        // Le vrai test : un seul enregistrement en base, pas deux.
        val all = publicationRepository.observeAll().first()
        assertEquals(1, all.size)
    }
}
