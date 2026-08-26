package com.inktone.feature.library

import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakePreAnalysisStore
import com.inktone.core.testing.fake.FakeRenderedPageCache
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeTtsSegmentCache
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.usecase.DeletePublicationUseCase
import com.inktone.domain.usecase.ToggleFavoriteUseCase
import com.inktone.domain.usecase.TogglePinUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Lot 2a.4 — même patron que LibraryViewModelTest, filtre serveur fixé par la route. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun publication(id: String, seriesName: String? = null, subjects: List<String> = emptyList()) = Publication(
        id = id, title = "Titre $id", format = PublicationFormat.EPUB,
        fileUri = "content://fake/$id", fileHash = "hash-$id", fileSize = 100L,
        chapterCount = 1, importDate = 0L, seriesName = seriesName, subjects = subjects,
    )

    @Test
    fun charge_uniquement_les_publications_de_la_serie_demandee() = runTest {
        val repository = FakePublicationRepository()
        repository.insert(publication("1", seriesName = "Trilogie du Vide"))
        repository.insert(publication("2", seriesName = "Trilogie du Vide"))
        repository.insert(publication("3", seriesName = "Autre série"))
        val viewModel = LibraryDetailViewModel(repository, FakeReadingStateRepository(), ToggleFavoriteUseCase(repository), TogglePinUseCase(repository), DeletePublicationUseCase(repository, FakePreAnalysisStore(), FakeTtsSegmentCache(), FakeRenderedPageCache()), dispatcher)

        viewModel.onIntent(LibraryDetailIntent.Load(LibraryDetailCategory.SERIES, "Trilogie du Vide"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("1", "2"), viewModel.state.value.displayedPublications.map { it.id })
    }

    @Test
    fun charge_uniquement_les_publications_du_tag_demande() = runTest {
        val repository = FakePublicationRepository()
        repository.insert(publication("1", subjects = listOf("Fantasy")))
        repository.insert(publication("2", subjects = listOf("SF")))
        val viewModel = LibraryDetailViewModel(repository, FakeReadingStateRepository(), ToggleFavoriteUseCase(repository), TogglePinUseCase(repository), DeletePublicationUseCase(repository, FakePreAnalysisStore(), FakeTtsSegmentCache(), FakeRenderedPageCache()), dispatcher)

        viewModel.onIntent(LibraryDetailIntent.Load(LibraryDetailCategory.TAG, "Fantasy"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("1"), viewModel.state.value.displayedPublications.map { it.id })
    }
}
