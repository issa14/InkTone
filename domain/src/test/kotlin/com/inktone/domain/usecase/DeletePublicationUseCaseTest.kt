package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakePreAnalysisStore
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lot 22, Palier A — la purge de la pré-analyse à la suppression d'une
 * publication est explicite (décision 1 : le fichier n'est pas couvert
 * par le `ON DELETE CASCADE` de Room). Ce test fige le critère de sortie
 * « la pré-analyse disparaît du disque à la suppression ».
 */
class DeletePublicationUseCaseTest {

    @Test
    fun `supprimer une publication purge aussi sa pre-analyse`() = runTest {
        val publicationRepository = FakePublicationRepository()
        val preAnalysisStore = FakePreAnalysisStore()
        val publication = Publication(
            id = "pub-1", title = "Titre", format = PublicationFormat.EPUB,
            fileUri = "content://fake/pub-1", fileHash = "hash-1", fileSize = 100L,
            chapterCount = 1, importDate = 0L,
        )
        publicationRepository.insert(publication)

        val useCase = DeletePublicationUseCase(publicationRepository, preAnalysisStore)
        useCase("pub-1")

        assertTrue(publicationRepository.observeAll().first().isEmpty())
        assertEquals(listOf("pub-1"), preAnalysisStore.deleted)
    }
}
