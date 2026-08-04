package com.inktone.feature.library

import com.inktone.core.testing.fake.FakeImportProgressObserver
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.service.ImportProgress
import com.inktone.domain.usecase.DeletePublicationUseCase
import com.inktone.domain.usecase.ToggleFavoriteUseCase
import com.inktone.domain.usecase.TogglePinUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun publication(id: String) = Publication(
        id = id, title = "Titre $id", format = PublicationFormat.EPUB,
        fileUri = "content://fake/$id", fileHash = "hash-$id", fileSize = 100L,
        chapterCount = 1, importDate = 0L,
    )

    @Test
    fun `expose les publications observees par le repository`() = runTest {
        val repository = FakePublicationRepository()
        repository.insert(publication("pub-1"))
        val viewModel = LibraryViewModel(repository, FakeReadingStateRepository(), ToggleFavoriteUseCase(repository), TogglePinUseCase(repository), DeletePublicationUseCase(repository), FakeImportProgressObserver())

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.publications.size)
        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test
    fun `ouvrir une publication emet un effet de navigation`() = runTest {
        val repository = FakePublicationRepository()
        repository.insert(publication("pub-1"))
        val viewModel = LibraryViewModel(repository, FakeReadingStateRepository(), ToggleFavoriteUseCase(repository), TogglePinUseCase(repository), DeletePublicationUseCase(repository), FakeImportProgressObserver())
        dispatcher.scheduler.advanceUntilIdle()

        var effect: LibraryEffect? = null
        val job = launch(dispatcher) { viewModel.effects.collect { effect = it } }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onIntent(LibraryIntent.OpenPublication("pub-1"))
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()
        assertTrue(effect is LibraryEffect.NavigateToReader)
        assertEquals("pub-1", (effect as LibraryEffect.NavigateToReader).publicationId)
    }

    @Test
    fun `reflete la progression d'import observee`() = runTest {
        val importProgressObserver = FakeImportProgressObserver()
        val viewModel = LibraryViewModel(FakePublicationRepository(), FakeReadingStateRepository(), ToggleFavoriteUseCase(FakePublicationRepository()), TogglePinUseCase(FakePublicationRepository()), DeletePublicationUseCase(FakePublicationRepository()), importProgressObserver)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ImportProgress(), viewModel.state.value.importProgress)

        importProgressObserver.emit(ImportProgress(current = 3, total = 10, hasQueuedChunks = true))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ImportProgress(current = 3, total = 10, hasQueuedChunks = true), viewModel.state.value.importProgress)
    }
}
