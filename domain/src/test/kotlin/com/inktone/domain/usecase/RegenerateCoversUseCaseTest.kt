package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.service.CoverExtractionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lot 19 — reconstruction des couvertures : ré-écrit la couverture extraite, signale la progression X/Y, isole les échecs. */
class RegenerateCoversUseCaseTest {

    private fun publication(id: String, format: PublicationFormat = PublicationFormat.EPUB) = Publication(
        id = id, title = "Titre $id", format = format,
        fileUri = "content://fake/$id", fileHash = "hash-$id", fileSize = 100L,
        chapterCount = 1, importDate = 0L, coverUri = "couverture-originale-$id.jpg",
    )

    @Test
    fun `reconstruit la couverture de chaque publication et rapporte la progression`() = runTest {
        val repository = FakePublicationRepository()
        repository.insert(publication("pub-1"))
        repository.insert(publication("pub-2"))
        val parser = FakePublicationParser()
        parser.setCoverResult("couverture-reconstruite.jpg")

        val useCase = RegenerateCoversUseCase(repository, parser)
        val progress = mutableListOf<Pair<Int, Int>>()
        val result = useCase { processed, total -> progress += processed to total }

        assertEquals(CoverRegenerationResult(processed = 2, failed = 0), result)
        assertEquals(listOf(1 to 2, 2 to 2), progress)
        assertEquals("couverture-reconstruite.jpg", repository.getById("pub-1")?.coverUri)
        assertEquals("couverture-reconstruite.jpg", repository.getById("pub-2")?.coverUri)
    }

    @Test
    fun `une couverture absente remet la couverture par defaut sans compter un echec`() = runTest {
        val repository = FakePublicationRepository()
        repository.insert(publication("pub-1"))
        val parser = FakePublicationParser()
        parser.setCoverResult(null)

        val result = RegenerateCoversUseCase(repository, parser)()

        assertEquals(CoverRegenerationResult(processed = 1, failed = 0), result)
        assertNull(repository.getById("pub-1")?.coverUri)
    }

    @Test
    fun `un echec d'ouverture est compte et preserve la couverture existante`() = runTest {
        val repository = FakePublicationRepository()
        repository.insert(publication("pub-1"))
        repository.insert(publication("pub-2"))
        val parser = FakePublicationParser()
        parser.setExtractCoverHandler { fileUri ->
            if (fileUri.endsWith("pub-1")) CoverExtractionResult.Failure else CoverExtractionResult.Success("couverture-pub-2.jpg")
        }

        val result = RegenerateCoversUseCase(repository, parser)()

        assertEquals(CoverRegenerationResult(processed = 2, failed = 1), result)
        // pub-1 a échoué : sa couverture d'origine est préservée, pas écrasée.
        assertEquals("couverture-originale-pub-1.jpg", repository.getById("pub-1")?.coverUri)
        assertEquals("couverture-pub-2.jpg", repository.getById("pub-2")?.coverUri)
    }

    @Test
    fun `un parser qui leve malgre le contrat est isole en echec sans ecrasement`() = runTest {
        val repository = FakePublicationRepository()
        repository.insert(publication("pub-1"))
        val parser = FakePublicationParser()
        parser.setExtractCoverHandler { error("levée inattendue") }

        val result = RegenerateCoversUseCase(repository, parser)()

        assertEquals(CoverRegenerationResult(processed = 1, failed = 1), result)
        assertEquals("couverture-originale-pub-1.jpg", repository.getById("pub-1")?.coverUri)
    }
}
