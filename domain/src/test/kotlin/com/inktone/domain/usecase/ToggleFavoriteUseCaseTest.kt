package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleFavoriteUseCaseTest {

    @Test
    fun `bascule le favori d'une publication existante`() = runTest {
        val repository = FakePublicationRepository()
        val publication = Publication(
            id = "pub-1", title = "Les Misérables", format = PublicationFormat.EPUB,
            fileUri = "content://fake/1", fileHash = "hash1", fileSize = 1000L,
            chapterCount = 10, importDate = 0L,
        )
        repository.insert(publication)

        ToggleFavoriteUseCase(repository)(publication.id, isFavorite = true)

        assertTrue(repository.getById(publication.id)!!.isFavorite)
    }
}
