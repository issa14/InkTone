package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakeChapterParser
import com.inktone.core.testing.fake.FakeFileStorageService
import com.inktone.core.testing.fake.FakePreAnalysisStore
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeSearchService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
            searchService = FakeSearchService(),
            chapterParser = FakeChapterParser(),
            preAnalysisStore = FakePreAnalysisStore(),
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

    @Test
    fun `deux imports concurrents du meme fichier ne creent pas de doublon (Tache 6_3)`() = runTest {
        val publicationRepository = FakePublicationRepository()
        // delayMs > 0 elargit deliberement la fenetre entre la verification
        // hors verrou et la reverification sous mutex - sans ca, le fake
        // n'a aucune IO reelle et les deux coroutines ne s'entrelaceraient
        // jamais (reproduirait un faux negatif, pas une preuve d'absence
        // de course - la meme discipline que K2 avec le legacy).
        val importPublication = ImportPublicationUseCase(
            publicationParser = FakePublicationParser(delayMs = 10),
            publicationRepository = publicationRepository,
            fileStorageService = FakeFileStorageService(),
            searchService = FakeSearchService(),
            chapterParser = FakeChapterParser(),
            preAnalysisStore = FakePreAnalysisStore(),
        )
        val fixtureUri = "content://fake/fixture-minimal.epub"

        val results = listOf(
            async { importPublication(fixtureUri) },
            async { importPublication(fixtureUri) },
        ).awaitAll()

        assertEquals(1, results.count { it is ImportResult.Success })
        assertEquals(1, results.count { it is ImportResult.Duplicate })
        assertEquals(1, publicationRepository.observeAll().first().size)
    }
}
